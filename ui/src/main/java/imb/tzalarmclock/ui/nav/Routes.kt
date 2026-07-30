package imb.tzalarmclock.ui.nav

/** Top-level navigation destinations for the four pages in the spec. */
sealed class Routes(val route: String) {
    data object Summary : Routes("summary")

    /** Also the create flow: [buildRoute] with no id navigates here for a new alarm. */
    data object Details : Routes("details?alarmId={alarmId}") {
        const val ARG_ALARM_ID = "alarmId"
        const val NO_ALARM_ID = -1L

        fun buildRoute(alarmId: Long? = null): String = "details?alarmId=${alarmId ?: NO_ALARM_ID}"
    }

    data object Ringing : Routes("ringing")
    data object Settings : Routes("settings")
}
