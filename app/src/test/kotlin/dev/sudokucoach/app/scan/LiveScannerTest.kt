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

/** Contorno, estado, estabilidad y votación del escáner (tareas 4.2 y 4.3). */
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
        assertFalse(scanner.onFrame(square(0.8f), 0))
        assertEquals(LiveFrame(square(0.8f).outline, LiveStatus.HOLD_STILL), scanner.state.value)
        assertTrue(scanner.onFrame(square(0.8f), 300))
        assertEquals(LiveStatus.READING, scanner.state.value.status)
    }

    @Test
    fun `un tablero pequeno pide acercarse y no se lee`() {
        val scanner = scanner()
        assertFalse(scanner.onFrame(square(0.4f), 0)) // 16 % del fotograma
        assertFalse(scanner.onFrame(square(0.4f), 400))
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
            assertTrue(scanner.onFrame(square(0.8f), t))
            assertNull(scanner.onReading(reading, t + 200))
        }
        assertTrue(scanner.onFrame(square(0.8f), 700))
        val board = assertNotNull(scanner.onReading(reading, 900))
        assertEquals(5, board.values[0])
        assertEquals(DigitOrigin.GIVEN, board.origins[0])
        assertEquals(900L, board.millis) // desde que el tablero se quedó quieto, en 0
        assertFalse(scanner.onFrame(square(0.8f), 950))
        assertNull(scanner.onReading(reading, 1000))
    }

    @Test
    fun `moverse empieza la votacion de cero`() {
        val scanner = scanner()
        scanner.onFrame(square(0.8f), 0)
        scanner.onFrame(square(0.8f), 300)
        scanner.onReading(reading, 400)
        scanner.onReading(reading, 500)
        assertFalse(scanner.onFrame(square(0.8f, shift = 0.05f), 550))
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
        assertTrue(scanner.onFrame(square(0.8f), 1300))
    }
}
