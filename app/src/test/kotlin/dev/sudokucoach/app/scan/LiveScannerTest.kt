package dev.sudokucoach.app.scan

import dev.sudokucoach.app.camera.BufferPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contorno y línea de estado del escáner (tarea 4.2). */
class LiveScannerTest {
    /** Cuadrado centrado que ocupa [side] × [side] del fotograma. */
    private fun square(side: Float): List<BufferPoint> {
        val a = (1 - side) / 2
        val b = a + side
        return listOf(BufferPoint(a, a), BufferPoint(b, a), BufferPoint(b, b), BufferPoint(a, b))
    }

    @Test
    fun `empieza buscando`() {
        assertEquals(LiveFrame.Searching, LiveScanner().state.value)
    }

    @Test
    fun `un tablero grande pide quedarse quieto y uno pequeno acercarse`() {
        val scanner = LiveScanner(minAreaFraction = 0.25)
        scanner.onFrame(square(0.8f))
        assertEquals(LiveFrame(square(0.8f), LiveStatus.HOLD_STILL), scanner.state.value)
        scanner.onFrame(square(0.4f)) // 16 % del fotograma
        assertEquals(LiveStatus.TOO_FAR, scanner.state.value.status)
    }

    @Test
    fun `el contorno aguanta unos fotogramas sin tablero antes de perderse`() {
        val scanner = LiveScanner(graceFrames = 2)
        scanner.onFrame(square(0.8f))
        repeat(2) { scanner.onFrame(null) }
        assertEquals(square(0.8f), scanner.state.value.outline)
        scanner.onFrame(null)
        assertEquals(LiveFrame.Searching, scanner.state.value)
    }

    @Test
    fun `un tablero vuelve a contar los fotogramas sin tablero desde cero`() {
        val scanner = LiveScanner(graceFrames = 2)
        scanner.onFrame(square(0.8f))
        repeat(2) { scanner.onFrame(null) }
        scanner.onFrame(square(0.7f))
        repeat(2) { scanner.onFrame(null) }
        assertEquals(square(0.7f), scanner.state.value.outline)
    }

    @Test
    fun `al soltar la camara se deja de ver el tablero`() {
        val scanner = LiveScanner()
        scanner.onFrame(square(0.8f))
        scanner.reset()
        assertNull(scanner.state.value.outline)
        assertEquals(LiveStatus.SEARCHING, scanner.state.value.status)
    }
}
