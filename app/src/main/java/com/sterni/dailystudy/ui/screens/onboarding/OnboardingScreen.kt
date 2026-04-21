package com.sterni.dailystudy.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Shield,
        title = "ברוכים הבאים ל-Tether",
        subtitle = "מערכת הגנה חכמה שמאפשרת לקהילה שלך לנהל את המכשיר בצורה בטוחה ומאובטחת"
    ),
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "הגנה ברמת המערכת",
        subtitle = "Tether עובד ברמת מערכת ההפעלה — לא ניתן לעקוף, לא ניתן להסיר, עובד גם בלי אינטרנט"
    ),
    OnboardingPage(
        icon = Icons.Default.People,
        title = "מבוסס קהילות",
        subtitle = "כל קהילה מגדירה את ההגדרות שלה. הצטרף לקהילה שלך עם קוד שקיבלת מהמנהל"
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(6f)
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Dots indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            repeat(pages.size) { i ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == i) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < pages.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < pages.size - 1) "הבא" else "בוא נתחיל",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (pagerState.currentPage < pages.size - 1) {
            TextButton(onClick = onFinished) {
                Text("דלג", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.subtitle,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}
