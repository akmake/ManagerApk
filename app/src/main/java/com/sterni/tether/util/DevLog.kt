package com.sterni.tether.util

import android.content.Context
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app diagnostic console with persistent storage.
 *
 * Captures THIS app's own logcat output so the internal "detected / not detected / error" stream
 * can be inspected inside the app (like the browser F12 console) without adb. Every line is also
 * appended to a rolling file under the app's private storage, so the history **survives app
 * restarts / process death** and can be exported in full — it is never wiped unless the user taps
 * "clear".
 *
 * Framework noise (HWUI, vulkan, Zygote, …) is filtered out so it can neither bury nor evict the
 * app's own diagnostics; warnings and errors are always kept.
 *
 * Started once from [com.sterni.tether.TetherApp.onCreate]; the diagnostics screen observes
 * [entries] (recent, in-memory) and shares [exportText] (full, from disk).
 */
object DevLog {
    data class Entry(val time: String, val level: Char, val tag: String, val message: String)

    private const val MEM_MAX = 2000                     // recent lines kept in memory for the live view
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024  // rotate the current file at ~4 MB
    private const val EMIT_MIN_INTERVAL_MS = 250L

    private const val FILE_CUR = "devlog.log"
    private const val FILE_PREV = "devlog.1.log"

    private val buffer = ArrayDeque<Entry>()
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Volatile private var started = false
    @Volatile private var lastEmit = 0L

    private var dir: File? = null
    private var writer: BufferedWriter? = null
    private var bytesWritten = 0L

    // logcat "threadtime": "MM-DD HH:MM:SS.mmm  PID  TID L TAG: message"
    private val LINE =
        Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d)\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s?(.*)$""")

    // Our own log tags. Framework noise in our process is dropped; W/E are always kept.
    private val APP_TAGS = listOf(
        "Tether", "PolicySync", "Geofence", "AdminLogin", "Zmanim", "AppBlocker", "DevLog"
    )

    private fun keep(tag: String, level: Char): Boolean =
        level == 'W' || level == 'E' || APP_TAGS.any { tag.startsWith(it) }

    fun start(context: Context) {
        if (started) return
        started = true
        val filesDir = context.applicationContext.filesDir
        Thread({
            synchronized(lock) {
                dir = filesDir
                val cur = File(filesDir, FILE_CUR)
                bytesWritten = if (cur.exists()) cur.length() else 0L
                openWriter()
                loadHistory()
            }
            try {
                val pid = Process.myPid()
                // -T 1: only new lines from now on (don't re-ingest the ring on every restart — the
                // file already holds our history), filtered to our own process.
                val proc = ProcessBuilder("logcat", "--pid=$pid", "-T", "1", "-v", "threadtime")
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().forEachLine { handleLine(it) }
            } catch (t: Throwable) {
                addEntry(Entry(now(), 'E', "DevLog", "logcat capture failed: ${t.message}"), force = true)
            }
        }, "DevLog-logcat").apply { isDaemon = true; start() }
    }

    private fun handleLine(line: String) {
        val m = LINE.find(line) ?: return
        val level = m.groupValues[2][0]
        val tag = m.groupValues[3].trim()
        if (!keep(tag, level)) return
        addEntry(Entry(m.groupValues[1], level, tag, m.groupValues[4]))
        appendToFile(line)
    }

    private fun addEntry(entry: Entry, force: Boolean = false) {
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MEM_MAX) buffer.removeFirst()
            val ts = System.currentTimeMillis()
            if (force || ts - lastEmit >= EMIT_MIN_INTERVAL_MS) {
                lastEmit = ts
                _entries.value = buffer.toList()
            }
        }
    }

    // ── persistence ──────────────────────────────────────────────────────────

    private fun openWriter() {
        writer = try { BufferedWriter(FileWriter(File(dir, FILE_CUR), true)) } catch (_: Exception) { null }
    }

    private fun appendToFile(line: String) {
        synchronized(lock) {
            val w = writer ?: return
            try {
                w.write(line); w.write("\n")
                bytesWritten += line.length + 1
                if (bytesWritten >= MAX_FILE_BYTES) rotate() else w.flush()
            } catch (_: Exception) {}
        }
    }

    private fun rotate() {
        try {
            writer?.flush(); writer?.close()
            val d = dir ?: return
            val prev = File(d, FILE_PREV)
            if (prev.exists()) prev.delete()
            File(d, FILE_CUR).renameTo(prev)
            openWriter()
            bytesWritten = 0L
        } catch (_: Exception) {}
    }

    /** Loads the tail of the persisted files into the in-memory buffer so history shows at once. */
    private fun loadHistory() {
        val d = dir ?: return
        val loaded = ArrayList<Entry>()
        listOf(File(d, FILE_PREV), File(d, FILE_CUR)).forEach { f ->
            if (!f.exists()) return@forEach
            try {
                f.bufferedReader().useLines { seq ->
                    seq.forEach { line -> LINE.find(line)?.let { m ->
                        loaded.add(Entry(m.groupValues[1], m.groupValues[2][0], m.groupValues[3].trim(), m.groupValues[4]))
                    } }
                }
            } catch (_: Exception) {}
        }
        val tail = if (loaded.size > MEM_MAX) loaded.subList(loaded.size - MEM_MAX, loaded.size) else loaded
        buffer.clear(); buffer.addAll(tail)
        _entries.value = buffer.toList()
    }

    /** Wipes the in-memory buffer AND the persisted files (and the underlying logcat ring). */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
            try { writer?.close() } catch (_: Exception) {}
            dir?.let { d ->
                File(d, FILE_CUR).delete()
                File(d, FILE_PREV).delete()
            }
            bytesWritten = 0L
            openWriter()
        }
        try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")) } catch (_: Exception) {}
    }

    /** Full saved history (previous + current file), for share/export. */
    fun exportText(): String = synchronized(lock) {
        val d = dir ?: return@synchronized buffer.joinToString("\n") { "${it.time} ${it.level} ${it.tag}: ${it.message}" }
        val sb = StringBuilder()
        listOf(File(d, FILE_PREV), File(d, FILE_CUR)).forEach { f ->
            if (f.exists()) try { sb.append(f.readText()) } catch (_: Exception) {}
        }
        sb.toString().ifBlank {
            buffer.joinToString("\n") { "${it.time} ${it.level} ${it.tag}: ${it.message}" }
        }
    }

    private fun now(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
