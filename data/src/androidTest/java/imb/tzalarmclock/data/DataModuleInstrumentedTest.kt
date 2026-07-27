package imb.tzalarmclock.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataModuleInstrumentedTest {
    @Test
    fun targetContextIsAppPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("imb.tzalarmclock.data.test", appContext.packageName)
    }
}
