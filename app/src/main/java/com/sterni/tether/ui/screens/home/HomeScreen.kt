package com.sterni.tether.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sterni.tether.data.model.Study
import com.sterni.tether.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onStudyClick: (studyKey: String, date: String, title: String, label: String) -> Unit,
    onZmanimClick: () -> Unit = {}, onMamaarimClick: () -> Unit = {}, onCalendarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}, onToolsClick: () -> Unit = {}, onNewsClick: () -> Unit = {},
    onTefilaClick: () -> Unit = {}, onPdfLibraryClick: () -> Unit = {}, onEnterAdmin: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dateOffset by remember { mutableIntStateOf(0) }
    val date = remember(dateOffset) { LocalDate.now().plusDays(dateOffset.toLong()) }
    LaunchedEffect(dateOffset) { viewModel.loadDailyStudy(date.toString()) }
    HomeContent(state, date, onStudyClick, onZmanimClick, onMamaarimClick, onCalendarClick,
        onSettingsClick, onToolsClick, onNewsClick, onTefilaClick, onPdfLibraryClick, onEnterAdmin,
        onPrevDay = { dateOffset-- }, onNextDay = { dateOffset++ },
        onRefresh = { viewModel.loadDailyStudy(date.toString()) })
}

@Composable
fun HomeContent(
    state: HomeUiState, date: LocalDate,
    onStudyClick: (String, String, String, String) -> Unit,
    onZmanimClick: () -> Unit = {}, onMamaarimClick: () -> Unit = {}, onCalendarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}, onToolsClick: () -> Unit = {}, onNewsClick: () -> Unit = {},
    onTefilaClick: () -> Unit = {}, onPdfLibraryClick: () -> Unit = {}, onEnterAdmin: () -> Unit = {},
    onPrevDay: () -> Unit = {}, onNextDay: () -> Unit = {}, onRefresh: () -> Unit = {}
) {
    var secretHolds by remember { mutableIntStateOf(0) }
    TetherScaffold(
        topBar = { HomeHeader(date, state.hebrewDate, onSettingsClick, onRefresh, onPrevDay, onNextDay,
            onSecretHold = { if (++secretHolds >= 5) { secretHolds = 0; onEnterAdmin() } }, onAdminClick = onEnterAdmin) },
        bottomBar = {
            TetherNavigationBar {
                TetherNavigationBarItem(true, {}, { Icon(Icons.Default.Home, null) }, label = { Text("בית") })
                TetherNavigationBarItem(false, onZmanimClick, { Icon(Icons.Default.Schedule, null) }, label = { Text("זמנים") })
                TetherNavigationBarItem(false, onNewsClick, { Icon(Icons.Default.Feed, null) }, label = { Text("חדשות") })
                TetherNavigationBarItem(false, onCalendarClick, { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("לוח") })
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { TetherSectionTitle("גישה מהירה", Modifier.padding(top = 0.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeShortcut("תפילה", Icons.AutoMirrored.Filled.MenuBook, onTefilaClick, Modifier.weight(1f))
                        HomeShortcut("מאמרים", Icons.Default.Article, onMamaarimClick, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeShortcut("ספרייה", Icons.Default.LibraryBooks, onPdfLibraryClick, Modifier.weight(1f))
                        HomeShortcut("כלים", Icons.Default.Tune, onToolsClick, Modifier.weight(1f))
                    }
                }
            }
            item { TetherSectionTitle("לימודי היום") }
            when {
                state.isLoading -> item {
                    TetherSettingsGroup {
                        repeat(4) { index ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                                Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                                Spacer(Modifier.height(10.dp))
                                Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                            }
                            if (index < 3) TetherGroupDivider()
                        }
                    }
                }
                state.error != null -> item { HomeError(state.error, onRefresh) }
                else -> item {
                    val keys = listOf("chumash", "tehillim", "tanya", "rambam", "rambamOne", "shnayimMikra")
                    val studies = keys.mapNotNull { key -> state.studies[key]?.let { key to it } }
                    TetherSettingsGroup {
                        if (studies.isEmpty()) TetherPreferenceRow("אין לימודים זמינים לתאריך הזה", Icons.AutoMirrored.Filled.MenuBook)
                        studies.forEachIndexed { index, (key, study) ->
                            StudyItem(study) { onStudyClick(key, state.date, study.title.orEmpty(), study.label.orEmpty()) }
                            if (index < studies.lastIndex) TetherGroupDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHeader(
    displayDate: LocalDate, hebrewDate: String, onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit, onPrevDay: () -> Unit, onNextDay: () -> Unit,
    onSecretHold: () -> Unit = {}, onAdminClick: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tether", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HeaderAction(Icons.Default.ManageAccounts, "אזור מנהל", onAdminClick)
            Spacer(Modifier.width(6.dp))
            HeaderAction(Icons.Default.Refresh, "רענן", onRefreshClick)
            Spacer(Modifier.width(6.dp))
            HeaderAction(Icons.Default.Settings, "הגדרות", onSettingsClick)
        }
        Spacer(Modifier.height(14.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevDay) { Icon(Icons.Default.ChevronRight, "יום קודם") }
                Column(Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = onSecretHold), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), style = MaterialTheme.typography.titleMedium)
                    Text(hebrewDate.ifBlank { "לימוד יומי" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNextDay) { Icon(Icons.Default.ChevronLeft, "יום הבא") }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HomeShortcut(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    TetherCard(modifier = modifier, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StudyItem(study: Study, onClick: () -> Unit) {
    val available = study.available == true
    TetherPreferenceRow(study.title.orEmpty(), Icons.AutoMirrored.Filled.MenuBook,
        subtitle = if (available) study.subtitle ?: study.preview?.take(95) else "לא נמצאו נתונים להיום",
        onClick = if (available) onClick else null,
        trailing = {
            if (available) Column(horizontalAlignment = Alignment.End) {
                study.label?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 88.dp), textAlign = TextAlign.End)
                }
                Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        })
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    TetherCard {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
            Text("לא ניתן לטעון את הלימודים", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            TetherButton(onRetry) { Text("נסה שוב") }
        }
    }
}
