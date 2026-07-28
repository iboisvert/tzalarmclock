package imb.tzalarmclock.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import imb.tzalarmclock.ui.details.DetailsScreen
import imb.tzalarmclock.ui.ringing.RingingScreen
import imb.tzalarmclock.ui.settings.SettingsScreen
import imb.tzalarmclock.ui.summary.SummaryScreen

@Composable
fun TzAlarmClockNavHost(
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
                onAddAlarm = { navController.navigate(Routes.Details.route) },
                onOpenSettings = { navController.navigate(Routes.Settings.route) },
            )
        }
        composable(Routes.Details.route) {
            DetailsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Ringing.route) {
            RingingScreen(onDismiss = { navController.popBackStack() })
        }
        composable(Routes.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPreviewRingingScreen = { navController.navigate(Routes.Ringing.route) },
            )
        }
    }
}
