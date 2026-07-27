package imb.tzalarmclock.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmModuleInstrumentedTest {
    @Test
    fun targetContextIsAppPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("imb.tzalarmclock.alarm.test", appContext.packageName)
    }
}
