package imb.tzalarmclock.ui.nav

/**
 * Top-level navigation destinations hosted in `MainActivity`'s NavHost.
 *
 * The Ringing page isn't here: it needs to show over the lock screen and on
 * top of other apps regardless of whether this app is even running, so it's a
 * dedicated `RingingActivity` (in the `app` module) launched via full-screen
 * intent, not a stacked destination in this graph.
 */
sealed class Routes(val route: String) {
    data object Summary : Routes("summary")

    /** Also the create flow: [buildRoute] with no id navigates here for a new alarm. */
    data object Details : Routes("details?alarmId={alarmId}") {
        const val ARG_ALARM_ID = "alarmId"
        const val NO_ALARM_ID = -1L

        fun buildRoute(alarmId: Long? = null): String = "details?alarmId=${alarmId ?: NO_ALARM_ID}"
    }

    data object Settings : Routes("settings")
}
