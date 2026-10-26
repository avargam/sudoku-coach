package dev.sudokucoach.app.scan

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.sudokucoach.app.camera.toGrayMat
import dev.sudokucoach.app.camera.unrotate
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.vision.MlKitGridReader
import dev.sudokucoach.vision.core.BoardDetector
import dev.sudokucoach.vision.core.BoardReader
import dev.sudokucoach.vision.core.BoardReading
import dev.sudokucoach.vision.core.live.Corner
import dev.sudokucoach.vision.core.live.toCellReadings
import java.io.Closeable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Análisis de cada fotograma de la cámara (D-21): detección del tablero en todos (tarea 4.1), cuyas
 * esquinas pasan a [scanner] (tarea 4.2), y lectura completa de los que [scanner] pide, con el
 * tablero quieto (tarea 4.3). La lectura reutiliza la detección del fotograma. Se ejecuta en un solo
 * hilo de fondo, así que mientras se lee (≈ 0,2 s) se descartan los fotogramas que llegan.
 *
 * Con [logStats] (solo en debug) escribe en logcat cada [STATS_FRAMES] fotogramas los tiempos de
 * conversión, detección y lectura, el retraso desde el sensor y los fotogramas por segundo, y cada
 * captura (tarea 4.7, `tools/vision/perf_session.sh`). Con [measure], en vez de
 * votar, lee el tablero completo en cada fotograma en que se detecta y escribe la lectura, para
 * comparar fotogramas entre sí y con la etiqueta de la foto (`tools/vision/live_measure.py`).
 * Mientras [recorder] graba (tarea 4.4), solo se detecta y se guarda cada fotograma: no se lee
 * ni se captura, para que la secuencia tenga todos los fotogramas.
 */
class FrameAnalyzer(
    private val scanner: LiveScanner,
    private val logStats: Boolean,
    private val measure: Boolean,
    private val recorder: FrameRecorder? = null,
) : ImageAnalysis.Analyzer, Closeable {
    private val detector = BoardDetector()
    private val recognizerInit = lazy { MlKitGridReader() }
    private val reader by lazy { BoardReader(recognizerInit.value, detector) }

    private val convertMillis = mutableListOf<Double>()
    private val detectMillis = mutableListOf<Double>()
    private val readMillis = mutableListOf<Double>()
    private val latencyMillis = mutableListOf<Double>()
    private var detected = 0
    private var windowStart = 0L

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val start = System.nanoTime()
            if (windowStart == 0L) windowStart = start
            // Del sensor al análisis: si los fotogramas se encolaran, crecería (tarea 4.7).
            latencyMillis += (SystemClock.elapsedRealtimeNanos() - frame.imageInfo.timestamp) / 1e6
            val gray = frame.toGrayMat()
            val converted = System.nanoTime()
            val scan = detector.detect(gray)
            val done = System.nanoTime()
            val rotation = frame.imageInfo.rotationDegrees
            val detection = scan?.let {
                Detection(
                    corners = it.corners.map { c -> Corner(c.x, c.y) },
                    outline = it.corners.map { c -> unrotate(c.x, c.y, gray.cols(), gray.rows(), rotation) },
                )
            }
            val recording = recorder?.recording?.value == true
            if (recording) recorder.save(gray, (start / 1e6).toLong(), rotation)
            val read = scanner.onFrame(detection, (done / 1e6).toLong()) && !recording
            if (scan != null) detected++
            convertMillis += (converted - start) / 1e6
            detectMillis += (done - converted) / 1e6

            when {
                scan == null -> Unit
                measure && !recording -> {
                    val reading = reader.read(scan)
                    Log.i(TAG, "read ms=${((System.nanoTime() - done) / 1e6).roundToInt()} ${reading.describe()}")
                    reading.release()
                }
                read -> {
                    val reading = reader.read(scan)
                    readMillis += (System.nanoTime() - done) / 1e6
                    val board = scanner.onReading(reading.toCellReadings(), (System.nanoTime() / 1e6).toLong())
                    reading.release()
                    if (logStats && board != null) {
                        Log.i(TAG, "capture reads=${scanner.reads} ms=${board.millis} suspicious=${ReviewBoard.from(board).suspicious.size}")
                    }
                }
                else -> scan.rectified.release()
            }
            if (logStats && convertMillis.size == STATS_FRAMES) {
                val seconds = (System.nanoTime() - windowStart) / 1e9
                Log.i(
                    TAG,
                    "frames size=${frame.width}x${frame.height} gray=${gray.cols()}x${gray.rows()} " +
                        "fps=${"%.1f".format(Locale.ROOT, STATS_FRAMES / seconds)} detected=$detected/$STATS_FRAMES " +
                        "convert=${convertMillis.summary()} detect=${detectMillis.summary()} " +
                        "latency=${latencyMillis.summary()} reads=${readMillis.size}" +
                        (if (readMillis.isEmpty()) "" else " read=${readMillis.summary()}"),
                )
                convertMillis.clear()
                detectMillis.clear()
                readMillis.clear()
                latencyMillis.clear()
                detected = 0
                windowStart = System.nanoTime()
            }
            gray.release()
        }
    }

    /** Se llama en el hilo del análisis, después del último fotograma. */
    override fun close() {
        scanner.reset()
        if (recognizerInit.isInitialized()) recognizerInit.value.close()
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
