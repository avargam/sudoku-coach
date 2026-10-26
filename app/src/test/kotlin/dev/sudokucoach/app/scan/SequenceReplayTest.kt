package dev.sudokucoach.app.scan

import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Units
import dev.sudokucoach.vision.core.live.CellReading
import dev.sudokucoach.vision.core.live.Corner
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reproducción de secuencias grabadas (tareas 4.4 y 4.8): formato de `frames.txt`, reloj simulado y datos reales. */
class SequenceReplayTest {
    private val format = FrameFormat(1080, 1920, 90)

    /** Tablero que ocupa buena parte del fotograma, quieto. */
    private val corners = listOf(Corner(100.0, 500.0), Corner(980.0, 500.0), Corner(980.0, 1380.0), Corner(100.0, 1380.0))
    private val reading = listOf(CellReading(5, 0.9f, inked = true, printed = true)) + List(80) { CellReading.EMPTY }

    /** Un fotograma cada 40 ms, con detección de 20 ms y lectura de 200 ms. */
    private fun frames(count: Int) = List(count) { RecordedFrame(40L * it, 20, corners, 200, reading) }

    @Test
    fun `el formato se lee como se escribe`() {
        val frames = listOf(frames(1)[0], RecordedFrame(40, 15, null, 0, null))
        val (parsedFormat, parsed) = FrameLog.parse(FrameLog.format(format, frames))
        assertEquals(format, parsedFormat)
        assertEquals(frames, parsed)
    }

    @Test
    fun `los fotogramas que llegan mientras se lee se descartan`() {
        val result = replay(frames(100), format)
        val board = assertNotNull(result.board)
        // Quieto desde el fotograma 0 (detectado a 20 ms): la primera lectura pide el de 320 ms y
        // tarda hasta 540; luego el de 560 (580–780) y el de 800 (820–1020). 3 lecturas, captura a 1020.
        assertEquals(3, result.reads)
        assertEquals(1000L, board.millis)
        assertTrue(result.analyzed < 100)
    }

    @Test
    fun `sin tablero no hay captura`() {
        assertNull(replay(List(50) { RecordedFrame(40L * it, 20, null, 0, null) }, format).board)
    }

    /**
     * Las secuencias reales con su `frames.txt` (de `SequenceScanTest`) y su etiqueta, de extremo a
     * extremo como `SequenceScanTest` (tarea 4.8): todas capturan, ninguna lectura errónea sin marcar
     * y, corrigiendo solo las sospechosas, la primera pista es la del motor sobre la etiqueta. Escribe
     * una línea por secuencia. Sin secuencias (en la CI), se salta.
     */
    private class Sequence(val id: String, val format: FrameFormat, val frames: List<RecordedFrame>, val label: List<Int>)

    /** Las secuencias con `frames.txt` y etiqueta; sin ninguna (en la CI), el test se salta. */
    private fun sequences(): List<Sequence> {
        val root = File(requireNotNull(System.getProperty("sudokucoach.sequences")) { "Falta la propiedad sudokucoach.sequences" })
        val dirs = root.listFiles { f -> File(f, "frames.txt").exists() && File(f, "label.txt").exists() }.orEmpty().sortedBy { it.name }
        assumeTrue("No hay secuencias reproducibles en $root", dirs.isNotEmpty())
        return dirs.map { dir ->
            val (format, frames) = FrameLog.parse(File(dir, "frames.txt").readText())
            val label = File(dir, "label.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.take(9)
                .joinToString("").map { if (it == '.') 0 else it.digitToInt() }
            Sequence(dir.name, format, frames, label)
        }
    }

    @Test
    fun `secuencias grabadas sin errores sin marcar`() {
        val unflagged = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (sequence in sequences()) {
            val label = sequence.label
            val result = replay(sequence.frames, sequence.format)
            val board = result.board
            if (board == null) {
                println("${sequence.id}: sin captura")
                failed += "${sequence.id} sin captura"
                continue
            }
            val review = ReviewBoard.from(board)
            val suspicious = review.suspicious
            val wrong = (0 until 81).filter { board.values[it] != label[it] }
            val missed = wrong - suspicious
            if (missed.isNotEmpty()) unflagged += "${sequence.id} ${missed.map(Units::cellName)}"
            // El usuario revisa las sospechosas: escribe el dígito correcto (o confirma el leído).
            val corrected = suspicious.sorted().fold(review) { b, cell -> b.edit(cell, label[cell]) }
            val validation = corrected.validate()
            val step = if (validation.allowsHints) HintState.start(corrected.values).step else null
            val expectedStep = HintEngine().nextStep(Grid.fromValues(label.toIntArray()))
            val hint = when {
                !validation.allowsHints || step != expectedStep -> "pista MAL".also { failed += "${sequence.id} pista" }
                expectedStep == null -> "tablero completo"
                else -> "pista ok (${expectedStep.technique.name})"
            }
            println("${sequence.id}: captura en ${board.millis} ms con ${result.reads} lecturas, ${suspicious.size} sospechosas, ${wrong.size} errores, sin marcar ${missed.map(Units::cellName)}, $hint")
        }
        assertTrue(unflagged.isEmpty(), "Lecturas erróneas sin marcar: $unflagged")
        assertTrue(failed.isEmpty(), "Secuencias sin captura o sin la primera pista correcta: $failed")
    }

    /**
     * Qué fotogramas se votan depende de cuándo empieza el escáner y de lo que tarda cada uno; con
     * una sola pasada, un dígito tenue puede salvarse de casualidad (tarea 4.8). Así que cada
     * secuencia se reproduce también empezando en 20 fotogramas distintos y con la segmentación
     * más rápida y más lenta: ninguna pasada puede dejar un error sin marcar.
     */
    @Test
    fun `secuencias grabadas sin errores sin marcar empiecen donde empiecen`() {
        val unflagged = mutableSetOf<String>()
        for (sequence in sequences()) for (offset in 0 until 60 step 3) for (inkMillis in listOf(5L, 20L)) {
            val board = replay(sequence.frames.drop(offset), sequence.format, inkMillis = inkMillis).board ?: continue
            val suspicious = ReviewBoard.from(board).suspicious
            (0 until 81).filter { board.values[it] != sequence.label[it] && it !in suspicious }
                .forEach { unflagged += "${sequence.id} ${Units.cellName(it)}" }
        }
        assertTrue(unflagged.isEmpty(), "Lecturas erróneas sin marcar en alguna pasada: $unflagged")
    }
}
