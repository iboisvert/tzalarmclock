package imb.tzalarmclock.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import imb.tzalarmclock.ui.timers.TimersScreen

/**
 * @param initialRoute a page to open on top of Summary once, e.g. when the app
 *   is launched to show a specific alarm. Summary stays the start destination
 *   so Back still lands there.
 */
@Composable
fun TzAlarmClockNavHost(
    versionInfo: String,
    modifier: Modifier = Modifier,
    initialRoute: String? = null,
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
                onOpenAlarms = { navController.navigate(Routes.Summary.route) },
                onOpenTimers = { navController.navigate(Routes.Timers.route) },
                onOpenSettings = { navController.navigate(Routes.Settings.route) },
            )
        }
        composable(Routes.Timers.route) {
            TimersScreen(
                onOpenAlarms = { navController.navigate(Routes.Summary.route) },
                onOpenTimers = { navController.navigate(Routes.Timers.route) },
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

    // Saveable so a configuration change, which recreates the activity with
    // the same launch intent, doesn't push the page a second time.
    var initialRouteOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialRoute) {
        if (initialRoute != null && !initialRouteOpened) {
            initialRouteOpened = true
            navController.navigate(initialRoute)
        }
    }
}
