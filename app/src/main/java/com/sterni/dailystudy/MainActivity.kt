package com.sterni.dailystudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.sterni.dailystudy.notification.CalendarReminderWorker
import com.sterni.dailystudy.ui.navigation.NavGraph
import com.sterni.dailystudy.ui.theme.DailyStudyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CalendarReminderWorker.enqueuePeriodic(this)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DailyStudyTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
