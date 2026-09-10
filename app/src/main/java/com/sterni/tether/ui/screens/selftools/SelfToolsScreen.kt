package com.sterni.tether.ui.screens.selftools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.tether.data.local.ShortsLimitPrefs
import com.sterni.tether.ui.theme.BaHaYetzira
import com.sterni.tether.ui.theme.Ink
import com.sterni.tether.ui.theme.LineColor
import com.sterni.tether.ui.theme.Muted
import com.sterni.tether.ui.theme.Primary
import com.sterni.tether.ui.theme.SblHebrew

/**
 * "Self tools" — restrictions a person sets on *themselves*, working alongside (and independently
 * of) the community policy. Reachable both from the regular user Settings and from the manager's
 * admin area, so a manager can rein in their own usage too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var shortsEnabled by remember { mutableStateOf(ShortsLimitPrefs.isEnabled(context)) }
    var shortsMinutes by remember { mutableStateOf(ShortsLimitPrefs.dailyLimitMinutes(context)) }
    // Bumped on a timer (and after adding bonus) so the usage readout stays live while the screen is open.
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(shortsEnabled) {
        while (shortsEnabled) {
            kotlinx.coroutines.delay(2000)
            refreshTick++
        }
    }

    val usedSeconds = remember(refreshTick, shortsEnabled) { ShortsLimitPrefs.usedSecondsToday(context) }
    val bonusToday = remember(refreshTick) { ShortsLimitPrefs.bonusMinutesToday(context) }
    val remainingBonus = remember(refreshTick) { ShortsLimitPrefs.remainingBonusMinutes(context) }
    val effectiveLimit = shortsMinutes + bonusToday

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("כלים עצמיים", fontFamily = BaHaYetzira, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "חזרה")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            item {
                Text(
                    "הגבלות שאתה מגדיר לעצמך. הן פועלות במקביל להגנת הקהילה.",
                    fontSize = 13.sp,
                    fontFamily = SblHebrew,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            item { SectionTitle("יוטיוב שורטס") }

            item {
                ToggleRow(
                    icon = Icons.Default.Timer,
                    label = "מגבלת זמן לשורטס",
                    description = "מגביל את הזמן היומי בשורטס. סרטונים רגילים נשארים חופשיים.",
                    checked = shortsEnabled,
                    onCheckedChange = {
                        shortsEnabled = it
                        ShortsLimitPrefs.setEnabled(context, it)
                    }
                )
            }

            if (shortsEnabled) {
                item {
                    UsageCard(usedSeconds = usedSeconds, limitMinutes = effectiveLimit)
                }

                item {
                    LimitInputRow(
                        value = shortsMinutes,
                        onValueChange = {
                            shortsMinutes = it
                            ShortsLimitPrefs.setDailyLimitMinutes(context, it)
                            refreshTick++
                        }
                    )
                }

                item {
                    AddMinutesRow(
                        bonusToday = bonusToday,
                        remainingBonus = remainingBonus,
                        onAdd = {
                            ShortsLimitPrefs.addBonusMinutes(context, ShortsLimitPrefs.BONUS_STEP_MIN)
                            refreshTick++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageCard(usedSeconds: Long, limitMinutes: Int) {
    val usedMinutes = (usedSeconds / 60L).toInt()
    val limitSeconds = limitMinutes * 60L
    val fraction = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f) else 0f
    val over = usedSeconds >= limitSeconds
    val remainingMin = ((limitSeconds - usedSeconds).coerceAtLeast(0L) / 60L).toInt()

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (over)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (over) "היום ניצלת את כל הזמן לשורטס" else "שימוש היום בשורטס",
                fontSize = 14.sp,
                fontFamily = SblHebrew,
                color = Muted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$usedMinutes / $limitMinutes דק׳",
                fontSize = 28.sp,
                fontFamily = SblHebrew,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (over) MaterialTheme.colorScheme.error else Primary,
                trackColor = LineColor.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (over) "הזמן נגמר — אפשר להוסיף דקות למטה" else "נותרו $remainingMin דק׳",
                fontSize = 12.sp,
                fontFamily = SblHebrew,
                color = Muted
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontFamily = SblHebrew,
        fontWeight = FontWeight.Bold,
        color = Primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontFamily = SblHebrew, color = Ink)
            Text(description, fontSize = 12.sp, fontFamily = SblHebrew, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = LineColor
            )
        )
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
}

/** Free numeric entry for the daily limit (any number in range), with quick ±5 buttons. */
@Composable
private fun LimitInputRow(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("מגבלה יומית (דקות)", fontSize = 16.sp, fontFamily = SblHebrew, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    val digits = new.filter { it.isDigit() }.take(4)
                    text = digits
                    digits.toIntOrNull()?.let {
                        onValueChange(it.coerceIn(ShortsLimitPrefs.MIN_LIMIT_MIN, ShortsLimitPrefs.MAX_LIMIT_MIN))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(110.dp)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                val n = (value - 5).coerceAtLeast(ShortsLimitPrefs.MIN_LIMIT_MIN)
                text = n.toString(); onValueChange(n)
            }) { Text("−5", fontFamily = SblHebrew) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val n = (value + 5).coerceAtMost(ShortsLimitPrefs.MAX_LIMIT_MIN)
                text = n.toString(); onValueChange(n)
            }) { Text("+5", fontFamily = SblHebrew) }
        }
        Text(
            "טווח: ${ShortsLimitPrefs.MIN_LIMIT_MIN}–${ShortsLimitPrefs.MAX_LIMIT_MIN} דקות",
            fontSize = 11.sp,
            fontFamily = SblHebrew,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
}

@Composable
private fun AddMinutesRow(
    bonusToday: Int,
    remainingBonus: Int,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("תוספת חד-פעמית להיום", fontSize = 16.sp, fontFamily = SblHebrew, color = Ink)
            Text(
                if (remainingBonus > 0)
                    "נוספו היום: $bonusToday דק׳ • אפשר עוד עד $remainingBonus דק׳"
                else
                    "הגעת למקסימום התוספת להיום (${ShortsLimitPrefs.MAX_BONUS_MIN} דק׳)",
                fontSize = 12.sp,
                fontFamily = SblHebrew,
                color = Muted
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onAdd,
            enabled = remainingBonus > 0,
            shape = MaterialTheme.shapes.small
        ) {
            Text("+${ShortsLimitPrefs.BONUS_STEP_MIN} דק׳", fontFamily = SblHebrew)
        }
    }
    HorizontalDivider(color = LineColor.copy(alpha = 0.6f), thickness = 0.5.dp)
}
