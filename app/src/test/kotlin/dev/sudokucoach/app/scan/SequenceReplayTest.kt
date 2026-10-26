package dev.sudokucoach.app.scan

import dev.sudokucoach.app.review.ReviewBoard
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

/** Reproducción de secuencias grabadas (tarea 4.4): formato de `frames.txt`, reloj simulado y datos reales. */
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
     * Las secuencias reales con su `frames.txt` (de `SequenceScanTest`) y su etiqueta: ninguna lectura
     * errónea sin marcar. Escribe una línea por secuencia. Sin secuencias (en la CI), se salta.
     */
    @Test
    fun `secuencias grabadas sin errores sin marcar`() {
        val root = File(requireNotNull(System.getProperty("sudokucoach.sequences")) { "Falta la propiedad sudokucoach.sequences" })
        val sequences = root.listFiles { f -> File(f, "frames.txt").exists() && File(f, "label.txt").exists() }.orEmpty().sortedBy { it.name }
        assumeTrue("No hay secuencias reproducibles en $root", sequences.isNotEmpty())
        val unflagged = mutableListOf<String>()
        for (dir in sequences) {
            val (format, frames) = FrameLog.parse(File(dir, "frames.txt").readText())
            val label = File(dir, "label.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.take(9)
                .joinToString("").map { if (it == '.') 0 else it.digitToInt() }
            val result = replay(frames, format)
            val board = result.board
            if (board == null) {
                println("${dir.name}: sin captura")
                continue
            }
            val suspicious = ReviewBoard.from(board).suspicious
            val wrong = (0 until 81).filter { board.values[it] != label[it] }
            val missed = wrong - suspicious
            if (missed.isNotEmpty()) unflagged += "${dir.name} ${missed.map(Units::cellName)}"
            println("${dir.name}: captura en ${board.millis} ms con ${result.reads} lecturas, ${suspicious.size} sospechosas, ${wrong.size} errores, sin marcar ${missed.map(Units::cellName)}")
        }
        assertTrue(unflagged.isEmpty(), "Lecturas erróneas sin marcar: $unflagged")
    }
}
