package imb.tzalarmclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

class AlarmTest {

    @Test
    fun `an alarm needs nothing but a time`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))

        assertEquals(Alarm.NO_ID, alarm.id)
        assertEquals("", alarm.name)
        assertNull(alarm.zone)
        assertEquals(AlarmSchedule.NextOccurrence, alarm.schedule)
        assertTrue(alarm.enabled)
        assertNull(alarm.ringtoneUri)
        assertNull(alarm.vibrate)
    }

    @Test
    fun `an alarm without a zone floats with the device`() {
        assertFalse(Alarm(time = LocalTime.of(7, 0)).isZoneLocked)
    }

    @Test
    fun `an alarm with a zone is locked to a fixed instant`() {
        val alarm = Alarm(time = LocalTime.of(7, 0), zone = ZoneId.of("America/Toronto"))

        assertTrue(alarm.isZoneLocked)
    }
}
