package com.sterni.dailystudy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sterni.dailystudy.ui.screens.calendar.CalendarScreen
import com.sterni.dailystudy.ui.screens.news.NewsScreen

sealed class Screen(val route: String) {
    object Calendar : Screen("calendar")
    object News     : Screen("news")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Calendar.route) {

        composable(Screen.Calendar.route) {
            CalendarScreen()
        }

        composable(Screen.News.route) {
            NewsScreen(onBack = { navController.popBackStack() })
        }
    }
}
