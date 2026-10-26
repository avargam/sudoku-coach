package dev.sudokucoach.app.scan

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grabación de secuencias de fotogramas (D-22, tarea 4.4), solo en debug. Cada sesión es una carpeta
 * `S<fecha>-<hora>` dentro de [root] con los fotogramas en gris del análisis, a tamaño completo
 * para que reproducirlos lea lo mismo que en vivo, como `NNNNNN.jpg` (ms desde el primero), y un
 * `session.txt` con el móvil y el tamaño. Se copian al PC con `tools/vision/pull_sequences.sh`.
 *
 * [start] y [stop] se llaman desde la interfaz y [save], desde el hilo del análisis. La grabación
 * se para sola a los [maxMillis], para no llenar el móvil si se olvida.
 */
class FrameRecorder(private val root: File, private val maxMillis: Long = 8_000) {
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    @Volatile
    private var session: File? = null
    private var first = -1L

    fun start() {
        val name = "S" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        session = File(root, name).apply { mkdirs() }
        first = -1L
        _recording.value = true
    }

    fun stop() {
        session?.let { Log.i(TAG, "secuencia ${it.name}: ${it.list()?.count { f -> f.endsWith(".jpg") }} fotogramas") }
        session = null
        _recording.value = false
    }

    /** Guarda el fotograma si se está grabando; [timeMillis] en cualquier reloj monótono. */
    fun save(gray: Mat, timeMillis: Long, rotationDegrees: Int) {
        val dir = session ?: return
        if (first < 0) {
            first = timeMillis
            File(dir, "session.txt").writeText(
                "device=${Build.MODEL} size=${gray.cols()}x${gray.rows()} rotation=$rotationDegrees quality=$QUALITY\n",
            )
        }
        val elapsed = timeMillis - first
        if (elapsed > maxMillis) {
            stop()
            return
        }
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, QUALITY)
        Imgcodecs.imwrite(File(dir, "%06d.jpg".format(Locale.ROOT, elapsed)).path, gray, params)
        params.release()
    }

    private companion object {
        const val TAG = "FrameRecorder"
        const val QUALITY = 90
    }
}
