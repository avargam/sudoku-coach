package dev.sudokucoach.vision

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Carga los nativos de OpenCV del AAR una sola vez (en la JVM lo hace Bytedeco en los tests de vision-core). */
object OpenCvAndroid {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        check(OpenCVLoader.initLocal()) { "No se pudieron cargar los nativos de OpenCV" }
        loaded = true
    }
}

/** Imagen en escala de grises, el formato que esperan `BoardDetector` y `GridSegmenter`. */
fun Bitmap.toGrayMat(): Mat {
    OpenCvAndroid.ensureLoaded()
    val rgba = Mat()
    Utils.bitmapToMat(this, rgba) // Android entrega RGBA, no el BGR habitual de OpenCV
    val gray = Mat()
    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
    rgba.release()
    return gray
}

/** Bitmap ARGB_8888 a partir de una imagen en gris o RGBA. */
fun Mat.toBitmap(): Bitmap {
    OpenCvAndroid.ensureLoaded()
    val rgba = Mat()
    when (channels()) {
        1 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_GRAY2RGBA)
        4 -> copyTo(rgba)
        else -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGR2RGBA)
    }
    val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgba, bitmap)
    rgba.release()
    return bitmap
}
