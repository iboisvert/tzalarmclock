package imb.tzalarmclock.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import imb.tzalarmclock.ui.details.DetailsScreen
import imb.tzalarmclock.ui.settings.SettingsScreen
import imb.tzalarmclock.ui.summary.SummaryScreen

@Composable
fun TzAlarmClockNavHost(
    versionInfo: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Summary.route,
        modifier = modifier,
    ) {
        composable(Routes.Summary.route) {
            SummaryScreen(
                onAddAlarm = { navController.navigate(Routes.Details.buildRoute()) },
                onEditAlarm = { alarmId -> navController.navigate(Routes.Details.buildRoute(alarmId)) },
                onOpenSettings = { navController.navigate(Routes.Settings.route) },
            )
        }
        composable(
            route = Routes.Details.route,
            arguments = listOf(
                navArgument(Routes.Details.ARG_ALARM_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.Details.NO_ALARM_ID
                },
            ),
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getLong(Routes.Details.ARG_ALARM_ID)
                ?: Routes.Details.NO_ALARM_ID
            DetailsScreen(
                alarmId = alarmId.takeIf { it != Routes.Details.NO_ALARM_ID },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() }, versionInfo = versionInfo)
        }
    }
}
