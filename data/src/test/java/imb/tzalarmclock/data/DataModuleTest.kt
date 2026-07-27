package imb.tzalarmclock.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataModuleTest {
    @Test
    fun `data module name is set`() {
        assertEquals("data", DataModule.NAME)
    }
}
