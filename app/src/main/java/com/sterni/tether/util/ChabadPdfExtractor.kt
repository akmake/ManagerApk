package com.sterni.tether.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

/**
 * Extracts text from Chabad-style maamar PDFs (custom Hebrew font encoding):
 *
 *  - processTextPosition: collects every glyph with X/Y/fontSize
 *  - hebrewMap + flipBrackets + reversed() per item  -> correct RTL Hebrew
 *  - fontSize < 11pt                                  -> drops footnotes
 *  - standalone digit lines                           -> drops page numbers
 *  - AUTO running-header detection (top-band + frequency, NOT hardcoded):
 *      a line is a running header if it sits in the top margin band of its page
 *      AND the same text recurs on >=2 pages (>=30% of pages). Such lines are
 *      removed only where they appear in the top band, so a legitimate opening
 *      title on page 1 is preserved.
 *  - gap > threshold -> word spacing ; sentence-end -> paragraph reconstruction
 *  - batch processing -> avoids OOM on large files
 */
class ChabadPdfExtractor : PDFTextStripper() {

    private val hebrewMap = mapOf(
        0xe0 to 'א', 0x2021 to 'א', 0xe1 to 'ב', 0x00B7 to 'ב',
        0xe2 to 'ג', 0x201A to 'ג', 0xe3 to 'ד', 0x201E to 'ד',
        0xe4 to 'ה', 0x2030 to 'ה', 0xe5 to 'ו', 0x00C2 to 'ו',
        0xe6 to 'ז', 0x00CA to 'ז', 0xe7 to 'ח', 0x00C1 to 'ח',
        0xe8 to 'ט', 0x00CB to 'ט', 0xe9 to 'י', 0x00C8 to 'י',
        0xC8 to 'י', 0xea to 'ך', 0x00CD to 'ך', 0xCD to 'ך',
        0xeb to 'כ', 0x00CE to 'כ', 0xec to 'ל', 0x00CF to 'ל',
        0xCF to 'ל', 0xed to 'ם', 0x00CC to 'ם', 0xee to 'מ',
        0x00D3 to 'מ', 0xD3 to 'מ', 0xef to 'ן', 0x00D4 to 'ן',
        0xf0 to 'נ', 0xF8FF to 'נ', 0xf1 to 'ס', 0x00D2 to 'ס',
        0xf2 to 'ע', 0x00DA to 'ע', 0xf3 to 'ף', 0x00DB to 'ף',
        0xf4 to 'פ', 0x00D9 to 'פ', 0xD9 to 'פ', 0xf5 to 'ץ',
        0x0131 to 'ץ', 0xf6 to 'צ', 0x02C6 to 'צ', 0xf7 to 'ק',
        0x02DC to 'ק', 0xf8 to 'ר', 0x00AF to 'ר', 0xf9 to 'ש',
        0x02D8 to 'ש', 0xfa to 'ת', 0x02D9 to 'ת'
    )

    // ── Internal state ────────────────────────────────────────────────────────
    private data class Item(
        val x: Float, val y: Float, val w: Float,
        val fontSize: Float, val text: String
    )

    /** A reconstructed line, keeping its top Y so headers can be located by position. */
    private data class Line(val y: Float, val text: String)

    private val pageItems   = mutableMapOf<Int, MutableList<Item>>()
    private val pageHeights = mutableMapOf<Int, Float>()

    init { sortByPosition = true }

    // ── PDFTextStripper hooks ──────────────────────────────────────────────────

    override fun startPage(page: PDPage) {
        super.startPage(page)
        val absPage = currentPageNo
        pageItems[absPage]   = mutableListOf()
        pageHeights[absPage] = page.mediaBox.height
    }

    override fun processTextPosition(text: TextPosition) {
        val raw = text.unicode ?: return
        if (raw.isBlank()) return

        val fixed = raw
            .map  { hebrewMap[it.code] ?: it }
            .map  { flipBrackets(it) }
            .reversed()
            .joinToString("")

        if (fixed.isBlank()) return

        val absPage = currentPageNo
        pageItems[absPage]?.add(
            Item(
                x        = text.x,
                y        = text.y,
                w        = text.width,
                fontSize = text.fontSizeInPt,
                text     = fixed
            )
        )
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) { }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun extract(
        document: PDDocument,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Pair<String, Int> {
        val pageCount = document.numberOfPages

        // Collect reconstructed lines (with Y) + height, per page.
        val pageLineLists  = ArrayList<List<Line>>(pageCount)
        val pageHeightList = ArrayList<Float>(pageCount)

        var processed = 0
        while (processed < pageCount) {
            val batchStart = processed + 1
            val batchEnd   = minOf(processed + BATCH_SIZE, pageCount)

            pageItems.clear()
            pageHeights.clear()
            startPage = batchStart
            endPage   = batchEnd

            getText(document)

            for (pageNum in batchStart..batchEnd) {
                pageLineLists.add(buildPageLines(pageNum))
                pageHeightList.add(pageHeights[pageNum] ?: 0f)
            }

            processed = batchEnd
            onProgress?.invoke(processed, pageCount)
        }

        // ── Auto-detect running headers: top-band texts that recur across pages ──
        val topCounts = HashMap<String, Int>()
        for (i in pageLineLists.indices) {
            val h = pageHeightList[i]
            if (h <= 0f) continue
            val seen = HashSet<String>()
            for (line in pageLineLists[i]) {
                if (line.y >= h * TOP_FRACTION) continue
                val key = normalize(line.text)
                if (key.length < 2) continue
                if (key.matches(DIGITS)) continue
                if (seen.add(key)) topCounts[key] = (topCounts[key] ?: 0) + 1
            }
        }
        val pages = pageLineLists.size
        val runningHeaders = topCounts
            .filterValues { it >= 2 && it >= 0.30 * pages }
            .keys

        // ── Flatten + clean (header removed only where it sits in the top band) ──
        val cleanLines = mutableListOf<String>()
        for (i in pageLineLists.indices) {
            val h = pageHeightList[i]
            for (line in pageLineLists[i]) {
                val t = line.text.trim()
                if (t.isEmpty())                       continue
                if (t.matches(DIGITS))                 continue   // page numbers
                if (t.contains("__") || t.contains("--")) continue
                if (t.length < 2)                      continue
                val inTopBand = h > 0f && line.y < h * TOP_FRACTION
                if (inTopBand && normalize(t) in runningHeaders) continue
                cleanLines.add(t.replace(Regex("\\s{2,}"), " "))
            }
        }

        // ── Paragraph reconstruction ────────────────────────────────────────────
        val paragraphs  = mutableListOf<String>()
        val currentPara = mutableListOf<String>()
        for (line in cleanLines) {
            currentPara.add(line)
            if (line.endsWith('.') || line.endsWith(':') || line.endsWith(';')) {
                paragraphs.add(currentPara.joinToString(" "))
                currentPara.clear()
            }
        }
        if (currentPara.isNotEmpty()) paragraphs.add(currentPara.joinToString(" "))

        return Pair(paragraphs.joinToString("\n\n"), pageCount)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ")

    private fun buildPageLines(pageNum: Int): List<Line> {
        val items = pageItems[pageNum] ?: return emptyList()

        val filtered = items.filter { item ->
            if (item.text.trim().isEmpty())     return@filter false
            if (item.fontSize in 0.01f..10.99f) return@filter false   // footnotes
            true
        }
        if (filtered.isEmpty()) return emptyList()

        val sortedByY  = filtered.sortedBy { it.y }
        val lineGroups = mutableListOf<MutableList<Item>>()
        for (item in sortedByY) {
            val last = lineGroups.lastOrNull()
            if (last == null || abs(item.y - last.first().y) > 5f)
                lineGroups.add(mutableListOf(item))
            else
                last.add(item)
        }

        return lineGroups.map { group ->
            val rtl = group.sortedByDescending { it.x }
            val text = buildString {
                var lastLeft = Float.MIN_VALUE
                for (item in rtl) {
                    if (lastLeft != Float.MIN_VALUE) {
                        val gap = lastLeft - (item.x + item.w)
                        val threshold = minOf(1.5f, item.fontSize * 0.15f)
                        if (gap > threshold) append(' ')
                    }
                    append(item.text)
                    lastLeft = item.x
                }
            }
            Line(group.first().y, text)
        }
    }

    private fun flipBrackets(c: Char) = when (c) {
        '(' -> ')'; ')' -> '('
        '[' -> ']'; ']' -> '['
        '{' -> '}'; '}' -> '{'
        '<' -> '>'; '>' -> '<'
        else -> c
    }

    companion object {
        private const val BATCH_SIZE   = 2500
        private const val TOP_FRACTION = 0.12f
        private val DIGITS = Regex("^\\d{1,4}$")
    }
}
