package imb.tzalarmclock.alarm.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SchedulingHealth.allClear] is what decides whether the Stage 9 startup
 * warning banner shows at all, so its three-way OR is pinned down here
 * without needing a device: any single problem must surface the banner, and
 * only the all-clear combination may suppress it.
 */
class SchedulingHealthTest {

    @Test
    fun allClearOnlyWhenNothingIsWrong() {
        assertTrue(
            SchedulingHealth(
                exactAlarmsAllowed = true,
                notificationsAllowed = true,
                batteryOptimized = false,
            ).allClear,
        )
    }

    @Test
    fun revokedExactAlarmsIsNotAllClear() {
        assertFalse(
            SchedulingHealth(
                exactAlarmsAllowed = false,
                notificationsAllowed = true,
                batteryOptimized = false,
            ).allClear,
        )
    }

    @Test
    fun deniedNotificationsIsNotAllClear() {
        assertFalse(
            SchedulingHealth(
                exactAlarmsAllowed = true,
                notificationsAllowed = false,
                batteryOptimized = false,
            ).allClear,
        )
    }

    @Test
    fun batteryOptimizedIsNotAllClear() {
        assertFalse(
            SchedulingHealth(
                exactAlarmsAllowed = true,
                notificationsAllowed = true,
                batteryOptimized = true,
            ).allClear,
        )
    }
}
