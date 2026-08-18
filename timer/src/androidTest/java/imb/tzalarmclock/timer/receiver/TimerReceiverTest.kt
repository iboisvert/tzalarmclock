package imb.tzalarmclock.timer.receiver

import android.app.ActivityManager
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.repository.TimerRepository
import imb.tzalarmclock.domain.schedule.start
import imb.tzalarmclock.timer.ringing.TimerRingingService
import imb.tzalarmclock.timer.schedule.TimerIntents
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * The fire path, driven through a real broadcast rather than by calling
 * `onReceive` directly — mirrors
 * `imb.tzalarmclock.alarm.receiver.AlarmReceiverTest`.
 */
@RunWith(AndroidJUnit4::class)
class TimerReceiverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val timers: TimerRepository = DataProvider.timerRepository(context)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val created = mutableListOf<Long>()

    @After
    fun cleanUp() = runBlocking {
        context.stopService(TimerRingingService.dismissAllIntent(context))
        created.forEach { timers.delete(it) }
    }

    private suspend fun saveRunning(): Long {
        val started = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.now())
            .start(Instant.now())
        val id = timers.save(started)
        created += id
        return id
    }

    private fun fire(timerId: Long) {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = TimerIntents.ACTION_TIMER_FIRED
            data = "tzalarmclock://timer/$timerId".toUri()
            putExtra(TimerIntents.EXTRA_TIMER_ID, timerId)
        }
        context.sendBroadcast(intent)
    }

    private fun isTimerRingingServiceRunning(): Boolean =
        activityManager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TimerRingingService::class.java.name }

    private suspend fun awaitRingingService() {
        withTimeout(TIMEOUT_MILLIS) {
            while (!isTimerRingingServiceRunning()) delay(POLL_MILLIS)
        }
    }

    private suspend fun awaitState(timerId: Long, state: TimerState) {
        withTimeout(TIMEOUT_MILLIS) {
            while (timers.getTimer(timerId)?.state != state) delay(POLL_MILLIS)
        }
    }

    @Test
    fun firingARunningTimerStartsTheRingingService() = runBlocking {
        val id = saveRunning()

        fire(id)

        awaitRingingService()
        assertTrue(isTimerRingingServiceRunning())
    }

    @Test
    fun firingARunningTimerFlipsItToExpired() = runBlocking {
        val id = saveRunning()

        fire(id)

        awaitState(id, TimerState.EXPIRED)
        assertEquals(TimerState.EXPIRED, timers.getTimer(id)?.state)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
