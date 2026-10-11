@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sterni.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.sterni.tether.data.model.*
import com.sterni.tether.ui.components.TetherScaffold
import com.sterni.tether.ui.components.TetherTopAppBar
import com.sterni.tether.ui.screens.home.HomeContent
import com.sterni.tether.ui.screens.home.HomeUiState
import com.sterni.tether.ui.screens.admin.*
import com.sterni.tether.ui.screens.settings.SettingsScreen
import com.sterni.tether.ui.screens.selftools.SelfToolsScreen
import com.sterni.tether.ui.screens.onboarding.OnboardingScreen
import com.sterni.tether.ui.screens.join.JoinCommunityScreen
import com.sterni.tether.ui.theme.TetherTheme
import java.time.LocalDate

/** Debug-only entry point. Fixtures never authenticate, enroll a device or change server data. */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        setContent {
            TetherTheme {
                when (screen) {
                    "home" -> HomeContent(
                        state = HomeUiState(date = "2026-10-10", hebrewDate = "לימוד יומי", studies = linkedMapOf(
                            "chumash" to Study(title = "חומש", subtitle = "לימוד פרשת השבוע", label = "השיעור היומי", available = true),
                            "tehillim" to Study(title = "תהילים", subtitle = "פרקי תהילים ליום", label = "לימוד יומי", available = true),
                            "tanya" to Study(title = "תניא", subtitle = "השיעור היומי בתניא", label = "לימוד יומי", available = true),
                            "rambam" to Study(title = "רמב״ם", subtitle = "שלושה פרקים ליום", available = true),
                            "rambamOne" to Study(title = "רמב״ם — פרק אחד", available = true),
                            "shnayimMikra" to Study(title = "שניים מקרא", available = true)
                        )), date = LocalDate.of(2026, 10, 10), onStudyClick = { _, _, _, _ -> })
                    "dashboard" -> AdminDashboardContent("מנהל הקהילה", true, DashboardStats(3, 12, 2, 0), listOf(
                        ActivityItem("DEVICE_ENROLLED", "מכשיר נוסף לקהילה", "קהילת הדוגמה", ""),
                        ActivityItem("APPROVAL_PENDING", "בקשה לאישור אפליקציה", "קהילת הדוגמה", "")
                    ))
                    "settings" -> SettingsScreen(onBack = {}, onEmergencyClick = {})
                    "selftools" -> SelfToolsScreen(onBack = {})
                    "login" -> AdminLoginScreen(onLoggedIn = {}, onBack = {})
                    "onboarding" -> OnboardingScreen(onFinished = {})
                    "join" -> JoinCommunityScreen(onCommunityFound = { _, _ -> })
                    "create" -> CreateCommunityScreen(onCreated = {}, onBack = {})
                    "share" -> ShareCommunityCodeScreen("DEMO1234", "קהילת הדוגמה", {})
                    "policy" -> TetherScaffold(topBar = { TetherTopAppBar(title = { Text("הגדרות קהילה") }) }) { padding ->
                        Box(Modifier.padding(padding)) { PolicyTab(CommunityPolicy(blockPack = true), false, {}) }
                    }
                    "device" -> TetherScaffold(topBar = { TetherTopAppBar(title = { Text("הגדרות מכשיר") }) }) { padding ->
                        Box(Modifier.padding(padding)) { DevicePolicyTab(DevicePolicy(), false, {}) }
                    }
                }
            }
        }
    }
}
