package com.sterni.tether.ui.screens.news

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sterni.tether.data.model.ArticleContent
import com.sterni.tether.data.model.NewsItem
import com.sterni.tether.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val PageBg  = Color(0xFFF2F2F7)
private val CardBg  = Color.White
private val Divider = Color(0xFFF0F0F0)

private val ChannelColors = mapOf(
    "amitsegal"      to (Color(0xFF38BDF8) to Color(0xFF0284C7)),
    "rotter"         to (Color(0xFFFB923C) to Color(0xFFEF4444)),
    "grinzaig"       to (Color(0xFFC084FC) to Color(0xFF9333EA)),
    "alexmehacarmel" to (Color(0xFF818CF8) to Color(0xFF2563EB)),
    "abualiexpress"  to (Color(0xFF34D399) to Color(0xFF059669))
)

private val ChannelLabels = mapOf(
    "amitsegal"      to "עמית סגל",
    "rotter"         to "רוטר",
    "grinzaig"       to "אבישי גרינצייג",
    "alexmehacarmel" to "אלכס מהכרמל",
    "abualiexpress"  to "אבו עלי אקספרס"
)

private fun timeAgo(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        )
        formats.forEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        val date = formats.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(dateStr) } catch (_: Exception) { null }
        } ?: return ""
        val diff = (System.currentTimeMillis() - date.time) / 1000
        when {
            diff < 60    -> "עכשיו"
            diff < 3600  -> "${diff / 60} דק׳"
            diff < 86400 -> "${diff / 3600} ש׳"
            else         -> "${diff / 86400} י׳"
        }
    } catch (_: Exception) { "" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBack: () -> Unit,
    vm: NewsViewModel = viewModel()
) {
    val state           by vm.state.collectAsStateWithLifecycle()
    val articleContents by vm.articleContents.collectAsStateWithLifecycle()
    val articleLoading  by vm.articleLoading.collectAsStateWithLifecycle()
    val articleErrors   by vm.articleErrors.collectAsStateWithLifecycle()
    val context         = LocalContext.current

    Scaffold(
        containerColor      = PageBg,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- MODERN HEADER ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PageBg)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה", tint = TetherInk, modifier = Modifier.size(20.dp))
                    }
                    
                    if (state.lastUpdated != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE8F5E9),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                                val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(state.lastUpdated!!))
                                Text("עודכן ב-$timeStr", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }

                    IconButton(
                        onClick = { vm.loadFeed() },
                        enabled = !state.loading,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PageBg)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "רענון", tint = TetherInk, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "עדכוני היום",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = HebrewFont,
                    color = TetherInk
                )
                Text(
                    text = "כל החדשות והעדכונים מהערוצים הנבחרים",
                    fontSize = 14.sp,
                    color = TetherMuted,
                    fontFamily = HebrewFont
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading && state.items.isEmpty() -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(5) { SkeletonCard() }
                        }
                    }

                    state.error != null && state.items.isEmpty() -> {
                        Column(
                            modifier            = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = TetherMuted, modifier = Modifier.size(40.dp))
                            Text(state.error ?: "", fontSize = 15.sp, fontFamily = HebrewFont, color = TetherMuted, textAlign = TextAlign.Center)
                            Button(
                                onClick = { vm.loadFeed() },
                                colors  = ButtonDefaults.buttonColors(containerColor = TetherBlue),
                                shape   = RoundedCornerShape(10.dp)
                            ) {
                                Text("נסה שוב", fontFamily = HebrewFont, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // --- ADMIN SUPPORT & MESSAGES (Pinned) ---
                            item {
                                AdminSupportCard()
                            }

                            items(state.items, key = { it.id ?: it.hashCode() }) { item ->
                                NewsFeedCard(
                                    item            = item,
                                    articleContent  = articleContents[item.link],
                                    articleLoading  = articleLoading.contains(item.link),
                                    articleError    = articleErrors.contains(item.link),
                                    onExpandArticle = { url -> vm.loadArticle(url) },
                                    onOpenLink      = { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                )
                            }

                            item {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("· · ·", fontSize = 16.sp, color = Color(0xFFD1D5DB), letterSpacing = 6.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ג”€ג”€ Feed Card ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun NewsFeedCard(
    item: NewsItem,
    articleContent: ArticleContent?,
    articleLoading: Boolean,
    articleError: Boolean,
    onExpandArticle: (String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    val sourceKey = if (item.source == "rotter") "rotter" else item.channel ?: ""
    val colors    = ChannelColors[sourceKey] ?: (Color(0xFF94A3B8) to Color(0xFF64748B))
    val label     = ChannelLabels[sourceKey] ?: sourceKey
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CardBg,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(colors.first, colors.second))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label.take(1), color = Color.White, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TetherInk)
                    Text(timeAgo(item.date), style = MaterialTheme.typography.labelSmall, color = TetherMuted)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onOpenLink(item.link ?: "") }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TetherMuted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Content
            if (!item.title.isNullOrBlank()) {
                Text(
                    text = item.title!!,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp),
                    color = TetherInk,
                    fontFamily = HebrewFont
                )
                Spacer(Modifier.height(8.dp))
            }

            if (!item.text.isNullOrBlank()) {
                Text(
                    text = item.text!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TetherInk.copy(alpha = 0.8f),
                    lineHeight = 22.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Media
            if (item.image != null || item.video != null) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))) {
                    if (item.image != null && item.video == null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(item.image).addHeader("Referer", "https://t.me/").crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item.video != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(item.videoThumb).addHeader("Referer", "https://t.me/").crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Expand Action
            if (item.link != null) {
                Button(
                    onClick = { 
                        expanded = !expanded
                        if (expanded && articleContent == null) onExpandArticle(item.link!!)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PageBg)
                ) {
                    if (articleLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TetherInk)
                    } else {
                        Text(if (expanded) "סגור" else "קרא עוד", color = TetherInk, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Article Content
            AnimatedVisibility(visible = expanded && articleContent != null) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Divider)
                    Spacer(Modifier.height(16.dp))
                    ArticleContentView(content = articleContent!!, articleUrl = item.link, onOpenLink = onOpenLink)
                }
            }
        }
    }
}

// ג”€ג”€ Article Content ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun ArticleContentView(
    content: ArticleContent,
    articleUrl: String?,
    onOpenLink: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        content.images?.forEach { imgUrl ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imgUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC))
            )
        }

        content.youtubeEmbeds?.forEach { embed ->
            if (embed.videoId != null) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            loadUrl("https://www.youtube.com/embed/${embed.videoId}?playsinline=1")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp))
                )
            }
        }

        content.twitterEmbeds?.forEach { tweetUrl ->
            val html = """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>body{margin:0;padding:0;background:#fff;}</style></head>
                <body><blockquote class="twitter-tweet" data-lang="he"><a href="$tweetUrl"></a></blockquote>
                <script async src="https://platform.twitter.com/widgets.js"></script></body></html>
            """.trimIndent()
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadDataWithBaseURL("https://twitter.com", html, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(10.dp))
            )
        }

        if (!content.text.isNullOrBlank()) {
            Text(
                text       = content.text!!,
                fontSize   = 14.sp,
                fontFamily = HebrewFont,
                color      = Color(0xFF374151),
                lineHeight = 24.sp,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
        }

        if (!content.comments.isNullOrEmpty()) {
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("תגובות", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = HebrewFont, color = TetherInk)
                Text("${content.comments!!.size}", fontSize = 11.sp, fontFamily = HebrewFont, color = TetherMuted)
            }
            content.comments!!.forEachIndexed { i, comment ->
                Row(
                    modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF8F8F8)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${i + 1}", fontSize = 11.sp, fontFamily = HebrewFont, color = TetherMuted, modifier = Modifier.width(18.dp))
                    Text(comment, fontSize = 13.sp, fontFamily = HebrewFont, color = Color(0xFF4B5563), lineHeight = 20.sp,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
                }
            }
        }

        if (articleUrl != null) {
            Row(
                modifier              = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpenLink(articleUrl) }.padding(vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("קריאה במקור", fontSize = 12.sp, fontFamily = HebrewFont, color = TetherBlue)
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TetherBlue, modifier = Modifier.size(11.dp))
            }
        }
    }
}

// ג”€ג”€ Skeleton ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

// ג”€ג”€ Support Card ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

@Composable
private fun AdminSupportCard() {
    val context = LocalContext.current
    val policy = remember { com.sterni.tether.admin.TetherPolicyManager.loadPolicy(context) }
    
    // אם אין פרטי קשר, נציג כרטיס ברירת מחדל יפה להודעות מנהל
    val hasSupport = policy?.supportWhatsApp != null || policy?.supportEmail != null
    val adminMsg = "ברוכים הבאים ל-DailyStudy. המנהל זמין כאן לכל שאלה."

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VerifiedUser, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = policy?.supportName ?: "מנהל הקהילה",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "סטטוס: מחובר להגנה",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                text = adminMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )
            
            if (hasSupport) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    policy?.supportWhatsApp?.let { phone ->
                        PremiumSupportButton(
                            modifier = Modifier.weight(1f),
                            label = "WhatsApp",
                            icon = Icons.Default.Chat,
                            containerColor = Color(0xFF25D366),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
                            }
                        )
                    }
                    
                    policy?.supportEmail?.let { email ->
                        PremiumSupportButton(
                            modifier = Modifier.weight(1f),
                            label = "אימייל",
                            icon = Icons.Default.Email,
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSupportButton(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SkeletonCard() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEEEEEE)))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(88.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)))
                Box(modifier = Modifier.width(50.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF4F4F5)))
            }
        }
        Spacer(Modifier.height(14.dp))
        listOf(1f, 0.9f, 0.7f).forEach { w ->
            Box(modifier = Modifier.fillMaxWidth(w).height(13.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)))
            Spacer(Modifier.height(8.dp))
        }
    }
}
