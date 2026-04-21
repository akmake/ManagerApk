package com.sterni.dailystudy.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sterni.dailystudy.ui.theme.TetherBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TetherBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Text(
                text = "Tether",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "הגנה חכמה למכשיר שלך",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
