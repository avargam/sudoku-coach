package dev.sudokucoach.app.scan

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.sudokucoach.app.camera.toGrayMat
import dev.sudokucoach.app.camera.unrotate
import dev.sudokucoach.vision.MlKitGridReader
import dev.sudokucoach.vision.core.BoardDetector
import dev.sudokucoach.vision.core.BoardReader
import dev.sudokucoach.vision.core.BoardReading
import java.io.Closeable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Análisis de cada fotograma de la cámara (D-21, tarea 4.1): solo detección del tablero, cuyas
 * esquinas pasan a [scanner] en coordenadas del búfer (tarea 4.2). Se ejecuta en un solo hilo de fondo.
 *
 * Con [logStats] (solo en debug) escribe en logcat cada [STATS_FRAMES] fotogramas los tiempos de
 * conversión y detección y los fotogramas por segundo. Con [measure] lee además el tablero
 * completo en cada fotograma en que se detecta y escribe la lectura, para comparar fotogramas
 * entre sí y con la etiqueta de la foto (`tools/live_measure.py`).
 */
class FrameAnalyzer(
    private val scanner: LiveScanner,
    private val logStats: Boolean,
    private val measure: Boolean,
) : ImageAnalysis.Analyzer, Closeable {
    private val detector = BoardDetector()
    private val recognizer by lazy { MlKitGridReader() }
    private val reader by lazy { BoardReader(recognizer, detector) }

    private val convertMillis = mutableListOf<Double>()
    private val detectMillis = mutableListOf<Double>()
    private var detected = 0
    private var windowStart = 0L

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val start = System.nanoTime()
            if (windowStart == 0L) windowStart = start
            val gray = frame.toGrayMat()
            val converted = System.nanoTime()
            val scan = detector.detect(gray)
            val done = System.nanoTime()
            scan?.rectified?.release()
            val rotation = frame.imageInfo.rotationDegrees
            scanner.onFrame(scan?.corners?.map { unrotate(it.x, it.y, gray.cols(), gray.rows(), rotation) })
            if (scan != null) detected++
            convertMillis += (converted - start) / 1e6
            detectMillis += (done - converted) / 1e6

            if (measure && scan != null) {
                val readStart = System.nanoTime()
                reader.read(gray)?.let { reading ->
                    Log.i(TAG, "read ms=${((System.nanoTime() - readStart) / 1e6).roundToInt()} ${reading.describe()}")
                    reading.release()
                }
            }
            if (logStats && convertMillis.size == STATS_FRAMES) {
                val seconds = (System.nanoTime() - windowStart) / 1e9
                Log.i(
                    TAG,
                    "frames size=${frame.width}x${frame.height} gray=${gray.cols()}x${gray.rows()} " +
                        "fps=${"%.1f".format(Locale.ROOT, STATS_FRAMES / seconds)} detected=$detected/$STATS_FRAMES " +
                        "convert=${convertMillis.summary()} detect=${detectMillis.summary()}",
                )
                convertMillis.clear()
                detectMillis.clear()
                detected = 0
                windowStart = System.nanoTime()
            }
            gray.release()
        }
    }

    /** Se llama en el hilo del análisis, después del último fotograma. */
    override fun close() {
        scanner.reset()
        if (measure) recognizer.close()
    }
}

/**
 * Lectura en una línea: dígitos, confianza en centésimas (99 como máximo), tinta y origen por
 * celda, en el orden de las etiquetas de `testdata/vision/labels`.
 */
private fun BoardReading.describe(): String {
    val digits = predictions.joinToString("") { it.digit.toString() }
    val confidences = predictions.joinToString(",") { (it.confidence * 100).toInt().coerceAtMost(99).toString() }
    val inked = cells.joinToString("") { if (it.empty) "." else "#" }
    val origins = cells.joinToString("") { if (it.empty) "." else if (it.printed) "P" else "M" }
    return "digits=$digits conf=$confidences inked=$inked origin=$origins"
}

private fun BoardReading.release() {
    scan.rectified.release()
    cells.forEach { it.digit?.release() }
}

/** Mediana y máximo en milisegundos. */
private fun List<Double>.summary(): String {
    val sorted = sorted()
    return "p50 ${"%.1f".format(Locale.ROOT, sorted[size / 2])} max ${"%.1f".format(Locale.ROOT, sorted.last())} ms"
}

private const val TAG = "LiveMeasure"
private const val STATS_FRAMES = 60
