package com.sterni.dailystudy.ui.screens.join

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun EnrollmentSuccessScreen(
    communityName: String,
    onContinue: () -> Unit
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
        delay(2500)
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(120.dp)
                .scale(scale.value)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "הצטרפת בהצלחה!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "המכשיר שלך מוגן על ידי קהילת",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = communityName,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("המשך", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
