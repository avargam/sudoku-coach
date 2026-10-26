package dev.sudokucoach.vision.core.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ¿Está quieto el tablero? (tarea 4.3). Tablero de 500 px de lado: 2 % son 10 px. */
class StabilityTrackerTest {
    private fun board(dx: Double = 0.0, dy: Double = 0.0) =
        listOf(Corner(100 + dx, 100 + dy), Corner(600 + dx, 100 + dy), Corner(600 + dx, 600 + dy), Corner(100 + dx, 600 + dy))

    @Test
    fun `quieto tras el tiempo minimo`() {
        val tracker = StabilityTracker(maxShift = 0.02, stillMillis = 300)
        assertFalse(tracker.update(board(), 0))
        assertFalse(tracker.update(board(dx = 3.0), 200))
        assertTrue(tracker.update(board(dx = -3.0, dy = 4.0), 300))
        assertEquals(0L, tracker.stillSince)
    }

    @Test
    fun `moverse reinicia el periodo`() {
        val tracker = StabilityTracker(maxShift = 0.02, stillMillis = 300)
        tracker.update(board(), 0)
        tracker.update(board(), 300)
        assertFalse(tracker.update(board(dx = 15.0), 330))
        assertEquals(330L, tracker.stillSince)
        assertTrue(tracker.update(board(dx = 15.0), 630))
    }

    @Test
    fun `un movimiento lento tambien reinicia`() {
        // 4 px por fotograma: nunca más de 10 px entre dos seguidos, pero sí desde el primero.
        val tracker = StabilityTracker(maxShift = 0.02, stillMillis = 300)
        val still = (0..10).map { tracker.update(board(dx = 4.0 * it), 40L * it) }
        assertFalse(still.last())
    }

    @Test
    fun `un hueco corto sin tablero no interrumpe y uno largo si`() {
        val tracker = StabilityTracker(stillMillis = 300, maxGapMillis = 200)
        tracker.update(board(), 0)
        assertFalse(tracker.update(null, 150))
        assertTrue(tracker.update(board(), 340))
        assertFalse(tracker.update(null, 600))
        assertNull(tracker.stillSince)
        assertFalse(tracker.update(board(), 620))
    }

    @Test
    fun `reset olvida el tablero`() {
        val tracker = StabilityTracker(stillMillis = 300)
        tracker.update(board(), 0)
        tracker.reset()
        assertFalse(tracker.update(board(), 400))
    }
}
