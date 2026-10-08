package dev.sudokucoach.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core

/** OpenCV 4.14 del AAR en el dispositivo y conversión Bitmap ⇄ Mat (tarea 2.7). */
@RunWith(AndroidJUnit4::class)
class OpenCvAndroidTest {

    @Test
    fun cargaLaMismaVersionQueVisionCore() {
        OpenCvAndroid.ensureLoaded()
        assertEquals("4.14", Core.VERSION.substring(0, 4))
    }

    @Test
    fun convierteBitmapAGrisYVuelve() {
        val bitmap = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixel(5, 7, Color.BLACK)
        }
        val gray = bitmap.toGrayMat()
        assertEquals(1, gray.channels())
        assertEquals(30, gray.cols())
        assertEquals(20, gray.rows())
        assertEquals(0.0, gray.get(7, 5)[0], 0.0)
        assertEquals(255.0, gray.get(0, 0)[0], 0.0)
        val back = gray.toBitmap()
        assertEquals(Color.BLACK, back.getPixel(5, 7))
    }
}
