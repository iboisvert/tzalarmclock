package imb.tzalarmclock.alarm.ringing

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.alarm.schedule.AlarmIntents
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Covers the repository/scheduler side effects the ring workflow needs, which
 * `AlarmReceiver` moved into [RingingService] as of Stage 6: a non-recurring
 * alarm retires on *dismiss*, not on fire (so it can actually be snoozed
 * first), and a snooze arms a new instant roughly one snooze period away
 * without touching `enabled`. Also covers [RingingService.isRinging]'s state
 * transitions, added for the unacknowledged-ring timeout feature.
 */
@RunWith(AndroidJUnit4::class)
class RingingServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarms: AlarmRepository = DataProvider.alarmRepository(context)
    private val snoozes = SnoozeRegistry(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val created = mutableListOf<Long>()

    @After
    fun cleanUp() = runBlocking {
        created.forEach {
            snoozes.clear(it)
            alarms.delete(it)
            RingingNotifications.cancelSnoozed(context, it)
        }
        RingingNotifications.cancelRinging(context)
    }

    private suspend fun save(schedule: AlarmSchedule): Long {
        val id = alarms.save(Alarm(time = LocalTime.of(7, 0), schedule = schedule, enabled = true))
        created += id
        return id
    }

    private suspend fun awaitAlarm(id: Long, predicate: (Alarm?) -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (!predicate(alarms.getAlarm(id))) delay(POLL_MILLIS)
        }
    }

    private suspend fun awaitSnooze(id: Long, predicate: () -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (!predicate()) delay(POLL_MILLIS)
        }
    }

    private suspend fun awaitRingingState(alarmId: Long, expected: Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (RingingService.isRinging(alarmId) != expected) delay(POLL_MILLIS)
        }
    }

    @Test
    fun dismissingANonRecurringAlarmDisablesIt() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)

        context.startService(RingingService.dismissIntent(context, id))

        awaitAlarm(id) { it?.enabled == false }
    }

    @Test
    fun dismissingARecurringAlarmLeavesItEnabledAndArmed() = runBlocking {
        val id = save(AlarmSchedule.Weekly(DayOfWeek.entries.toSet()))

        context.startService(RingingService.dismissIntent(context, id))

        awaitAlarm(id) { it != null && AlarmIntents.existing(context, id) != null }
        assertTrue(alarms.getAlarm(id)!!.enabled)
    }

    @Test
    fun dismissingClearsAnyPendingSnooze() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)
        snoozes.recordSnooze(id, System.currentTimeMillis() + 60_000)

        context.startService(RingingService.dismissIntent(context, id))

        awaitAlarm(id) { it?.enabled == false }
        assertNull(snoozes.snoozedUntilMillis(id))
    }

    @Test
    fun snoozingArmsAnInstantAboutOneSnoozePeriodAway() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)
        val before = System.currentTimeMillis()

        context.startService(RingingService.snoozeIntent(context, id))

        awaitSnooze(id) { snoozes.snoozedUntilMillis(id) != null }
        val expectedMillis = AppSettings.DEFAULT_SNOOZE_PERIOD_MINUTES * 60_000L
        val actualMillis = snoozes.snoozedUntilMillis(id)!! - before
        assertTrue(
            "expected roughly $expectedMillis ms from now, was $actualMillis ms",
            actualMillis in (expectedMillis - 5_000)..(expectedMillis + 15_000),
        )
    }

    @Test
    fun snoozingIncrementsTheSnoozeCountAndLeavesTheAlarmEnabled() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)

        context.startService(RingingService.snoozeIntent(context, id))

        awaitSnooze(id) { snoozes.snoozeCount(id) > 0 }
        assertEquals(1, snoozes.snoozeCount(id))
        assertTrue(alarms.getAlarm(id)!!.enabled)
    }

    /**
     * The snoozed notification must be posted under its own key, not over the
     * ringing notification's.
     *
     * They shared a key until this was fixed, so a snooze coming due re-posted
     * the ringing notification as an *update* to the one already showing —
     * and SystemUI only launches a full-screen intent for a notification it is
     * adding, never one it is updating. The effect was that an alarm ringing
     * again after a snooze left the device locked and showed no ringing screen
     * until the user unlocked it by hand.
     */
    @Test
    fun snoozingPostsItsOwnNotificationRatherThanReplacingTheRingingOne() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        val id = save(AlarmSchedule.NextOccurrence)

        context.startService(RingingService.snoozeIntent(context, id))

        awaitSnooze(id) { snoozes.snoozedUntilMillis(id) != null }
        // Shorter than TIMEOUT_MILLIS: the notification goes out in the same
        // breath as the snooze registry write awaited above, so it is already
        // due. Waiting the full ten seconds for a regression here only stalls
        // the suite long enough for an unrelated foreground-service start to
        // trip its own timeout and crash the process, hiding this failure
        // behind a confusing one attributed to whichever test ran next.
        withTimeout(NOTIFICATION_TIMEOUT_MILLIS) {
            while (
                notifications.activeNotifications.none {
                    it.id == RingingNotifications.notificationId(id) &&
                        it.tag == RingingNotifications.SNOOZED_TAG
                }
            ) {
                delay(POLL_MILLIS)
            }
        }
    }

    /**
     * [RingingService.isRinging] is what lets `RingingViewModel` notice a ring
     * cycle ending on its own — an unacknowledged-ring timeout auto-snoozing
     * or auto-dismissing, most notably — and close the ringing screen without
     * a tap here having caused it. Covers the state transition, not the
     * timeout itself: actually waiting out `ringTimeoutMinutes` (minimum one
     * real minute) belongs to manual/live verification, not this suite.
     */
    @Test
    fun ringingMarksTheAlarmActiveUntilDismissed() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)
        assertFalse(RingingService.isRinging(id))

        context.startService(RingingService.ringIntent(context, id))
        awaitRingingState(id, expected = true)

        context.startService(RingingService.dismissIntent(context, id))
        awaitRingingState(id, expected = false)
    }

    @Test
    fun snoozingAlsoEndsTheActiveRingImmediately() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)

        context.startService(RingingService.ringIntent(context, id))
        awaitRingingState(id, expected = true)

        context.startService(RingingService.snoozeIntent(context, id))
        awaitRingingState(id, expected = false)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val NOTIFICATION_TIMEOUT_MILLIS = 2_000L
        const val POLL_MILLIS = 50L
    }
}
