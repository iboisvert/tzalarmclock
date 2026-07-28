package imb.tzalarmclock.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmModuleTest {
    @Test
    fun `alarm module name is set`() {
        assertEquals("alarm", AlarmModule.NAME)
    }
}
