package dev.sudokucoach.app.camera

import androidx.camera.core.ImageProxy
import dev.sudokucoach.vision.OpenCvAndroid
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fotograma del análisis en gris (D-20, tarea 4.1): el plano Y del YUV 420, sin pasar por `Bitmap`,
 * girado con `rotationDegrees` para verlo derecho y, solo si pasa de [maxSide] px de lado mayor,
 * reducido. Los 1920 × 1080 del análisis se usan tal cual: en la tarea 4.1, reducirlos a 1600 px
 * como las fotos leía igual y costaba ≈ 7 ms por fotograma. Se copia, así que [ImageProxy] se
 * puede cerrar después.
 */
fun ImageProxy.toGrayMat(maxSide: Int = MAX_SIDE): Mat {
    OpenCvAndroid.ensureLoaded()
    val plane = planes[0]
    val buffer = plane.buffer.apply { rewind() }
    val stride = plane.rowStride
    // La última fila puede venir sin el relleno de rowStride; entonces el búfer no llega a
    // rowStride × height y se copia fila a fila.
    val y = if (buffer.capacity() >= stride * height) {
        Mat(height, width, CvType.CV_8UC1, buffer, stride.toLong())
    } else {
        Mat(height, width, CvType.CV_8UC1).also { mat ->
            val row = ByteArray(width)
            for (r in 0 until height) {
                buffer.position(r * stride)
                buffer.get(row)
                mat.put(r, 0, row)
            }
        }
    }
    val rotated = Mat()
    when (imageInfo.rotationDegrees) {
        90 -> Core.rotate(y, rotated, Core.ROTATE_90_CLOCKWISE)
        180 -> Core.rotate(y, rotated, Core.ROTATE_180)
        270 -> Core.rotate(y, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
        else -> y.copyTo(rotated)
    }
    y.release()

    val side = max(rotated.cols(), rotated.rows())
    if (side <= maxSide) return rotated
    val scale = maxSide.toDouble() / side
    val scaled = Mat()
    Imgproc.resize(rotated, scaled, Size((rotated.cols() * scale).roundToInt().toDouble(), (rotated.rows() * scale).roundToInt().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
    rotated.release()
    return scaled
}

private const val MAX_SIDE = 1920
