package imb.tzalarmclock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModuleTest {
    @Test
    fun `domain module builds and runs JVM unit tests without Android`() {
        assertEquals("domain", DomainModule.NAME)
    }
}
