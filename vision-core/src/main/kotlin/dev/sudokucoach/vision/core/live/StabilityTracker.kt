package dev.sudokucoach.vision.core.live

import kotlin.math.hypot

/** Esquina del tablero en píxeles de la imagen, en el orden de `BoardScan.corners`. */
data class Corner(val x: Double, val y: Double)

/**
 * Decide si el tablero está quieto (D-21, tarea 4.3) a partir de sus esquinas en cada fotograma.
 *
 * Las esquinas se comparan con las del primer fotograma del periodo quieto, no con las del anterior:
 * así un movimiento lento, que entre dos fotogramas no se nota, también acaba reiniciándolo. El
 * tablero está quieto cuando ninguna esquina se ha alejado más de [maxShift] (fracción del lado
 * medio del tablero) durante al menos [stillMillis].
 *
 * Con el tablero quieto, la detección falla en fotogramas sueltos; un hueco sin tablero de hasta
 * [maxGapMillis] no interrumpe el periodo.
 */
class StabilityTracker(
    private val maxShift: Double = 0.02,
    private val stillMillis: Long = 300,
    private val maxGapMillis: Long = 200,
) {
    private var anchor: List<Corner>? = null
    private var since = 0L
    private var lastSeen = 0L

    /** Momento en que empezó el periodo quieto actual, o `null` si no hay tablero a la vista. */
    val stillSince: Long? get() = if (anchor != null) since else null

    /** Añade un fotograma ([corners] `null` si no se detectó el tablero) y dice si está quieto. */
    fun update(corners: List<Corner>?, timeMillis: Long): Boolean {
        val current = anchor
        if (corners == null) {
            if (current != null && timeMillis - lastSeen > maxGapMillis) anchor = null
            return anchor != null && isStill(timeMillis)
        }
        require(corners.size == 4) { "Un tablero tiene 4 esquinas" }
        lastSeen = timeMillis
        if (current == null || moved(current, corners)) {
            anchor = corners
            since = timeMillis
        }
        return isStill(timeMillis)
    }

    fun reset() {
        anchor = null
    }

    private fun isStill(timeMillis: Long) = timeMillis - since >= stillMillis

    private fun moved(from: List<Corner>, to: List<Corner>): Boolean {
        val limit = maxShift * meanSide(from)
        return from.indices.any { hypot(to[it].x - from[it].x, to[it].y - from[it].y) > limit }
    }

    private fun meanSide(corners: List<Corner>) =
        corners.indices.sumOf { val a = corners[it]; val b = corners[(it + 1) % 4]; hypot(b.x - a.x, b.y - a.y) } / 4
}
