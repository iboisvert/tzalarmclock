package imb.tzalarmclock.ui.nav

/** Top-level navigation destinations for the four pages in the spec. */
sealed class Routes(val route: String) {
    data object Summary : Routes("summary")
    data object Details : Routes("details")
    data object Ringing : Routes("ringing")
    data object Settings : Routes("settings")
}
