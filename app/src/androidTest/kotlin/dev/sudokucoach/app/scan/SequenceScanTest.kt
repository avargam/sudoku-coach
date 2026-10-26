package dev.sudokucoach.app.scan

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.solver.Units
import dev.sudokucoach.vision.MlKitGridReader
import dev.sudokucoach.vision.OpenCvAndroid
import dev.sudokucoach.vision.core.BoardDetector
import dev.sudokucoach.vision.core.BoardReader
import dev.sudokucoach.vision.core.live.Corner
import dev.sudokucoach.vision.core.live.toCellReadings
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/**
 * Secuencias grabadas en vivo con el lector real (D-22, tarea 4.4). Cada fotograma pasa una vez por la
 * detección y, si hay tablero, por la lectura completa con ML Kit; el resultado se guarda en
 * `files/replays/<id>.txt` ([FrameLog]) para reproducirlo en la JVM (tarea 4.5), y aquí se reproduce
 * con [replay] como en vivo. Escribe por secuencia las sospechosas, los errores sin marcar y el
 * tiempo hasta la captura con la etiqueta `SequenceScanTest` del log.
 *
 * Los fotogramas se leen de donde los grabó la app (`files/sequences`), sin copiarlos; las etiquetas
 * se copian con `tools/vision/push_labels.sh` y los resultados se traen con
 * `tools/vision/pull_sequences.sh`. Sin secuencias, el test se salta.
 */
@RunWith(AndroidJUnit4::class)
class SequenceScanTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shellStream(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))

    private fun shell(command: String): String = shellStream(command).bufferedReader().readText()

    private val sequences = File(appContext.filesDir, "sequences")

    /** Los fotogramas de [id] por la detección y la lectura, cada uno una sola vez. */
    private fun analyze(id: String, reader: BoardReader, detector: BoardDetector): Pair<FrameFormat, List<RecordedFrame>> {
        val dir = File(sequences, id)
        val rotation = Regex("rotation=(\\d+)").find(File(dir, "session.txt").readText())?.groupValues?.get(1)?.toInt() ?: 90
        var size = 0 to 0
        val frames = dir.listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }.map { file ->
            val name = file.name
            val gray = Imgcodecs.imread(file.path, Imgcodecs.IMREAD_GRAYSCALE)
            size = gray.cols() to gray.rows()
            val start = System.nanoTime()
            val scan = detector.detect(gray)
            val detected = System.nanoTime()
            val reading = scan?.let { reader.read(it) }
            val read = System.nanoTime()
            gray.release()
            val cells = reading?.toCellReadings()
            reading?.let { r -> r.scan.rectified.release(); r.cells.forEach { it.digit?.release() } }
            RecordedFrame(
                timeMillis = name.removeSuffix(".jpg").toLong(),
                detectMillis = (detected - start) / 1_000_000,
                corners = scan?.corners?.map { Corner(it.x, it.y) },
                readMillis = (read - detected) / 1_000_000,
                reading = cells,
            )
        }
        return FrameFormat(size.first, size.second, rotation) to frames
    }

    private fun label(id: String): IntArray? = shell("cat $DIR/$id/label.txt").lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.take(9).joinToString("")
        .takeIf { it.length == 81 && '?' !in it }?.map { if (it == '.') 0 else it.digitToInt() }?.toIntArray()

    @Test
    fun secuenciasGrabadas() {
        val ids = sequences.list().orEmpty().filter { it.startsWith("S") }.sorted()
        assumeTrue("No hay secuencias en $sequences", ids.isNotEmpty())
        OpenCvAndroid.ensureLoaded()
        val out = File(appContext.filesDir, "replays").apply { mkdirs() }
        val unflagged = mutableListOf<String>()
        MlKitGridReader().use { recognizer ->
            val detector = BoardDetector()
            val reader = BoardReader(recognizer, detector)
            for (id in ids) {
                val (format, frames) = analyze(id, reader, detector)
                File(out, "$id.txt").writeText(FrameLog.format(format, frames))
                val result = replay(frames, format)
                val board = result.board
                val expected = label(id)
                if (board == null) {
                    Log.i(TAG, "$id: ${frames.size} fotogramas, sin captura (${result.reads} lecturas)")
                    continue
                }
                val suspicious = ReviewBoard.from(board).suspicious
                val wrong = expected?.let { e -> (0 until 81).filter { board.values[it] != e[it] } }
                val missed = wrong?.let { it - suspicious }.orEmpty()
                if (missed.isNotEmpty()) unflagged += "$id ${missed.map(Units::cellName)}"
                Log.i(
                    TAG,
                    "$id: ${frames.size} fotogramas, captura en ${board.millis} ms con ${result.reads} lecturas, " +
                        "${suspicious.size} sospechosas, ${wrong?.size ?: "?"} errores, sin marcar ${missed.map(Units::cellName)}" +
                        if (expected == null) " (sin etiqueta)" else "",
                )
            }
        }
        assertTrue("Lecturas erróneas sin marcar: $unflagged", unflagged.isEmpty())
    }

    private companion object {
        const val TAG = "SequenceScanTest"
        const val DIR = "/data/local/tmp/sudoku-sequences"
    }
}
