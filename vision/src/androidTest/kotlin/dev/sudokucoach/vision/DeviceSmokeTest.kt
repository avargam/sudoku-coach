package dev.sudokucoach.vision

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Comprueba que los tests instrumentados llegan al dispositivo desde WSL (tarea 2.0). */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {

    @Test
    fun seEjecutaEnElDispositivo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.sudokucoach.vision.test", context.packageName)
        assertTrue("API ${Build.VERSION.SDK_INT} < minSdk", Build.VERSION.SDK_INT >= 26)
        println("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
    }
}
