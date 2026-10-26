package dev.sudokucoach.app.scan

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.camera.BufferPoint
import dev.sudokucoach.vision.core.live.CellReading
import dev.sudokucoach.vision.core.live.CellVoter
import dev.sudokucoach.vision.core.live.Corner
import dev.sudokucoach.vision.core.live.StabilityTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contorno, estado, estabilidad y votación del escáner (tareas 4.2, 4.3 y 4.8). */
class LiveScannerTest {
    /** Cuadrado centrado que ocupa [side] × [side] del fotograma, desplazado [shift] (fracción). */
    private fun square(side: Float, shift: Float = 0f): Detection {
        val a = (1 - side) / 2 + shift
        val b = a + side
        val outline = listOf(BufferPoint(a, a), BufferPoint(b, a), BufferPoint(b, b), BufferPoint(a, b))
        return Detection(outline.map { Corner(it.u * 1000.0, it.v * 1000.0) }, outline)
    }

    private fun scanner() = LiveScanner(StabilityTracker(maxShift = 0.02, stillMillis = 300), CellVoter(minReads = 3, maxReads = 5))

    /** Un 5 impreso en la celda 0 y el resto vacío. */
    private val reading = listOf(CellReading(5, 0.9f, inked = true, printed = true)) + List(80) { CellReading.EMPTY }

    @Test
    fun `empieza buscando`() {
        assertEquals(LiveFrame.Searching, scanner().state.value)
    }

    @Test
    fun `un tablero grande pide quedarse quieto y luego se lee`() {
        val scanner = scanner()
        assertEquals(FrameAction.INK, scanner.onFrame(square(0.8f), 0)) // esperando, solo se segmenta
        assertEquals(LiveFrame(square(0.8f).outline, LiveStatus.HOLD_STILL), scanner.state.value)
        assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f), 300))
        assertEquals(LiveStatus.READING, scanner.state.value.status)
    }

    @Test
    fun `un tablero pequeno pide acercarse y no se lee`() {
        val scanner = scanner()
        assertEquals(FrameAction.NONE, scanner.onFrame(square(0.3f), 0)) // 9 % del fotograma
        assertEquals(FrameAction.NONE, scanner.onFrame(square(0.3f), 400))
        assertEquals(LiveStatus.TOO_FAR, scanner.state.value.status)
    }

    @Test
    fun `el contorno aguanta unos fotogramas sin tablero antes de perderse`() {
        val scanner = LiveScanner(graceFrames = 2)
        scanner.onFrame(square(0.8f), 0)
        repeat(2) { scanner.onFrame(null, 40L * (it + 1)) }
        assertEquals(square(0.8f).outline, scanner.state.value.outline)
        scanner.onFrame(null, 120)
        assertEquals(LiveFrame.Searching, scanner.state.value)
    }

    @Test
    fun `con la votacion completa sale el tablero una sola vez`() {
        val scanner = scanner()
        scanner.onFrame(square(0.8f), 0)
        for (t in listOf(300L, 500L)) {
            assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f), t))
            assertNull(scanner.onReading(reading, t + 200))
        }
        assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f), 700))
        val board = assertNotNull(scanner.onReading(reading, 900))
        assertEquals(5, board.values[0])
        assertEquals(DigitOrigin.GIVEN, board.origins[0])
        assertEquals(900L, board.millis) // desde que el tablero se quedó quieto, en 0
        assertEquals(FrameAction.NONE, scanner.onFrame(square(0.8f), 950))
        assertNull(scanner.onReading(reading, 1000))
    }

    @Test
    fun `el disparador manual captura con lo votado hasta entonces`() {
        val scanner = scanner()
        scanner.onFrame(square(0.8f), 0)
        scanner.onFrame(square(0.8f), 300)
        scanner.onReading(reading, 500)
        assertEquals(1, scanner.state.value.reads)
        scanner.captureNow()
        // En el siguiente fotograma, aunque se haya movido: sin leer otra vez.
        assertEquals(FrameAction.NONE, scanner.onFrame(square(0.8f, shift = 0.1f), 550))
        assertEquals(1, scanner.reads)
        assertEquals(FrameAction.NONE, scanner.onFrame(square(0.8f), 600)) // ya capturado
    }

    @Test
    fun `el disparador manual sin lecturas lee el siguiente fotograma con tablero`() {
        val scanner = scanner()
        scanner.captureNow()
        assertEquals(FrameAction.NONE, scanner.onFrame(null, 0)) // sin tablero, espera
        assertEquals(FrameAction.READ, scanner.onFrame(square(0.3f), 40)) // aunque esté lejos y no quieto
        val board = assertNotNull(scanner.onReading(reading, 240))
        assertEquals(5, board.values[0])
    }

    @Test
    fun `moverse empieza la votacion de cero`() {
        val scanner = scanner()
        scanner.onFrame(square(0.8f), 0)
        scanner.onFrame(square(0.8f), 300)
        scanner.onReading(reading, 400)
        scanner.onReading(reading, 500)
        assertEquals(FrameAction.INK, scanner.onFrame(square(0.8f, shift = 0.05f), 550))
        assertEquals(0, scanner.reads)
        assertEquals(LiveStatus.HOLD_STILL, scanner.state.value.status)
    }

    @Test
    fun `al soltar la camara se deja de ver el tablero y se puede volver a capturar`() {
        val scanner = scanner()
        scanner.onFrame(square(0.8f), 0)
        scanner.onFrame(square(0.8f), 300)
        repeat(3) { scanner.onReading(reading, 400) }
        scanner.reset()
        assertEquals(LiveFrame.Searching, scanner.state.value)
        scanner.onFrame(square(0.8f), 1000)
        assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f), 1300))
    }

    @Test
    fun `la tinta de la espera cuenta para las celdas dudosas y se olvida al moverse`() {
        val marked = listOf(true) + List(80) { false }
        val clean = List(81) { false }
        val empty = List(81) { CellReading.EMPTY }

        /** Espera con [before] fotogramas marcados, un movimiento, espera con [after] y 3 lecturas sin tinta. */
        fun capture(before: List<List<Boolean>>, after: List<List<Boolean>>): ScannedBoard {
            val scanner = LiveScanner(StabilityTracker(maxShift = 0.02, stillMillis = 300), CellVoter(minReads = 3, maxReads = 3, faintInkShare = 0.2))
            var t = 0L
            for (marks in before) {
                assertEquals(FrameAction.INK, scanner.onFrame(square(0.8f), t))
                scanner.onInk(marks)
                t += 40
            }
            for (marks in after) {
                assertEquals(FrameAction.INK, scanner.onFrame(square(0.8f, shift = 0.05f), t))
                scanner.onInk(marks)
                t += 40
            }
            t += 300
            repeat(2) {
                assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f, shift = 0.05f), t))
                assertNull(scanner.onReading(empty, t + 200))
                t += 220
            }
            assertEquals(FrameAction.READ, scanner.onFrame(square(0.8f, shift = 0.05f), t))
            return assertNotNull(scanner.onReading(empty, t + 200))
        }

        // Tinta en 2 de 3 fotogramas de la espera y en ninguna lectura: 2 de 6, dudosa.
        val faint = capture(before = emptyList(), after = listOf(marked, marked, clean))
        assertTrue(faint.inked[0])
        assertEquals(0, faint.values[0])
        assertFalse(faint.inked[1])
        // La tinta de antes de moverse no cuenta: 1 de 6, vacía (con ella, 4 de 9).
        val moved = capture(before = listOf(marked, marked, marked), after = listOf(marked, clean, clean))
        assertFalse(moved.inked[0])
    }
}
