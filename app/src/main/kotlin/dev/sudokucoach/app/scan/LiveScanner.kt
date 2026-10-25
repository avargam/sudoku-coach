package dev.sudokucoach.app.scan

import dev.sudokucoach.app.camera.BufferPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Lo que dice la línea de estado del escáner (tarea 4.2). */
enum class LiveStatus {
    /** No hay tablero en el visor. */
    SEARCHING,

    /** Hay tablero, pero ocupa poco del fotograma para leerlo bien. */
    TOO_FAR,

    /** Tablero a buena distancia: falta que se quede quieto (tarea 4.3). */
    HOLD_STILL,
}

/**
 * Estado del escáner para la interfaz: el contorno del tablero, con las esquinas en el orden de
 * `BoardScan.corners`, o `null`, y el estado.
 */
data class LiveFrame(val outline: List<BufferPoint>?, val status: LiveStatus) {
    companion object {
        val Searching = LiveFrame(null, LiveStatus.SEARCHING)
    }
}

/**
 * Escáner en vivo (D-21): recibe la detección de cada fotograma y decide qué se muestra. Kotlin puro,
 * sin Android, para probarlo en la JVM; en 4.3 se suman la estabilidad y la votación.
 *
 * La detección falla en fotogramas sueltos (un reflejo, un desenfoque), así que el último contorno
 * se mantiene durante [graceFrames] fotogramas sin tablero antes de darlo por perdido; si no, el
 * contorno parpadearía y TalkBack anunciaría cada cambio.
 */
class LiveScanner(
    private val graceFrames: Int = 5,
    /** Fracción del fotograma por debajo de la cual se pide acercarse. */
    private val minAreaFraction: Double = 0.25,
) {
    private val _state = MutableStateFlow(LiveFrame.Searching)
    val state: StateFlow<LiveFrame> = _state.asStateFlow()

    private var missed = 0

    /** Detección de un fotograma: las 4 esquinas en el búfer, o `null` si no hay tablero. */
    fun onFrame(corners: List<BufferPoint>?) {
        if (corners == null) {
            if (++missed > graceFrames) _state.value = LiveFrame.Searching
            return
        }
        require(corners.size == 4) { "Un tablero tiene 4 esquinas" }
        missed = 0
        val status = if (area(corners) < minAreaFraction) LiveStatus.TOO_FAR else LiveStatus.HOLD_STILL
        _state.value = LiveFrame(corners, status)
    }

    /** La cámara se ha soltado: no queda ningún tablero a la vista. */
    fun reset() {
        missed = 0
        _state.value = LiveFrame.Searching
    }
}

/** Área del cuadrilátero (fórmula del lazo); en coordenadas normalizadas, fracción del fotograma. */
private fun area(corners: List<BufferPoint>): Double {
    var twice = 0.0
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        twice += a.u.toDouble() * b.v - b.u.toDouble() * a.v
    }
    return abs(twice) / 2
}
