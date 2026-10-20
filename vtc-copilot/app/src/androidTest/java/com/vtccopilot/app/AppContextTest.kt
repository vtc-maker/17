package com.vtccopilot.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Test instrumenté minimal : vérifie que l'app se charge sur l'appareil. */
@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun useAppContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.vtccopilot.app", context.packageName)
    }
}
