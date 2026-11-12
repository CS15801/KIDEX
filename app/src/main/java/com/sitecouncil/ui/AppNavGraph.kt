package com.sitecouncil.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sitecouncil.ui.progress.ProgressScreen
import com.sitecouncil.ui.result.ResultScreen
import com.sitecouncil.ui.settings.SettingsScreen
import com.sitecouncil.ui.start.StartScreen

object Routes {
    const val START = "start"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val RESULT = "result/{id}"
    fun result(id: Long) = "result/$id"
}

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.START) {
        composable(Routes.START) {
            StartScreen(
                onStarted = { nav.navigate(Routes.PROGRESS) },
                onOpenResult = { id -> nav.navigate(Routes.result(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                onDone = { id ->
                    nav.navigate(Routes.result(id)) {
                        popUpTo(Routes.START)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            ResultScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
