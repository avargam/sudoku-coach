package dev.sudokucoach.app.scan

import dev.sudokucoach.app.camera.BufferPoint
import dev.sudokucoach.vision.core.live.CellReading
import dev.sudokucoach.vision.core.live.CellVoter
import dev.sudokucoach.vision.core.live.Corner
import dev.sudokucoach.vision.core.live.StabilityTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Lo que dice la línea de estado del escáner (tarea 4.2). */
enum class LiveStatus {
    /** No hay tablero en el visor. */
    SEARCHING,

    /** Hay tablero, pero ocupa poco del fotograma para leerlo bien. */
    TOO_FAR,

    /** Tablero a buena distancia: falta que se quede quieto. */
    HOLD_STILL,

    /** Tablero quieto: se leen fotogramas y se vota (tarea 4.3). */
    READING,
}

/**
 * Estado del escáner para la interfaz: el contorno del tablero, con las esquinas en el orden de
 * `BoardScan.corners`, o `null`, el estado y, leyendo, cuántas lecturas se han votado.
 */
data class LiveFrame(val outline: List<BufferPoint>?, val status: LiveStatus, val reads: Int = 0) {
    companion object {
        val Searching = LiveFrame(null, LiveStatus.SEARCHING)
    }
}

/** Qué hacer con un fotograma con tablero, según [LiveScanner.onFrame]. */
enum class FrameAction {
    NONE,

    /** Solo segmentar y pasar la tinta a [LiveScanner.onInk]: es barato (tarea 4.8). */
    INK,

    /** Leer el tablero completo y pasar la lectura a [LiveScanner.onReading]. */
    READ,
}

/**
 * Tablero detectado en un fotograma: [corners] en píxeles de la imagen, para la estabilidad, y
 * [outline] en coordenadas del búfer, para dibujarlo.
 */
class Detection(val corners: List<Corner>, val outline: List<BufferPoint>)

/**
 * Escáner en vivo (D-21): recibe la detección de cada fotograma y decide qué se muestra y cuándo
 * leer. Kotlin puro, sin Android, para probarlo en la JVM.
 *
 * 1. Cada fotograma da el contorno y alimenta la estabilidad ([StabilityTracker]).
 * 2. Con el tablero quieto y a buena distancia, [onFrame] pide leer el fotograma; cada lectura
 *    pasa a [onReading] y se vota ([CellVoter]). Mientras se espera a que quede quieto, pide solo
 *    segmentarlo ([onInk]), para que los dígitos a lápiz muy tenues tengan más fotogramas en los que
 *    verse (tarea 4.8). Si el tablero se mueve o se pierde, la votación empieza de cero.
 * 3. Cuando la votación está completa, el tablero votado sale una sola vez por [captures], con el
 *    tiempo desde que el tablero se quedó quieto.
 * 4. El disparador manual ([captureNow], tarea 4.6) captura en el siguiente fotograma con tablero:
 *    con lo votado hasta entonces o, si aún no hay ninguna lectura, leyendo ese fotograma, esté
 *    quieto o no.
 *
 * La detección falla en fotogramas sueltos (un reflejo, un desenfoque), así que el último contorno
 * se mantiene durante [graceFrames] fotogramas sin tablero antes de darlo por perdido; si no, el
 * contorno parpadearía y TalkBack anunciaría cada cambio.
 */
class LiveScanner(
    private val stability: StabilityTracker = StabilityTracker(),
    private val voter: CellVoter = CellVoter(),
    private val graceFrames: Int = 5,
    /**
     * Fracción del fotograma por debajo de la cual se pide acercarse: 15 % (tarea 4.5); con el 25 %
     * inicial, tal como se sostiene el móvil (el tablero ocupa el 23–30 %), muchas veces no se leía.
     */
    private val minAreaFraction: Double = 0.15,
) {
    private val _state = MutableStateFlow(LiveFrame.Searching)
    val state: StateFlow<LiveFrame> = _state.asStateFlow()

    private val _captures = MutableSharedFlow<ScannedBoard>(extraBufferCapacity = 1)
    val captures: SharedFlow<ScannedBoard> = _captures.asSharedFlow()

    private var missed = 0
    private var captured = false

    /** Inicio del periodo quieto con el que se está votando; si cambia, se vota de cero. */
    private var votingSince: Long? = null

    /** Pedido desde la interfaz; lo atiende el hilo del análisis en el siguiente fotograma. */
    @Volatile
    private var manual = false

    /** Lecturas con las que la votación termina siempre. */
    val maxReads: Int get() = voter.maxReads

    /** Disparador manual: captura en cuanto haya un tablero, sin esperar a la votación. */
    fun captureNow() {
        manual = true
    }

    /**
     * Detección de un fotograma ([detection] `null` si no hay tablero). Devuelve qué hacer con él:
     * leerlo y pasar la lectura a [onReading], solo segmentarlo y pasar la tinta a [onInk], o nada.
     */
    fun onFrame(detection: Detection?, timeMillis: Long): FrameAction {
        if (captured) return FrameAction.NONE
        if (detection == null) {
            stability.update(null, timeMillis)
            restartVoteIfMoved()
            if (++missed > graceFrames) _state.value = LiveFrame.Searching
            return FrameAction.NONE
        }
        require(detection.outline.size == 4) { "Un tablero tiene 4 esquinas" }
        missed = 0
        val near = area(detection.outline) >= minAreaFraction
        val still = if (near) {
            stability.update(detection.corners, timeMillis)
        } else {
            stability.reset()
            false
        }
        if (!manual) restartVoteIfMoved()
        val status = when {
            !near -> LiveStatus.TOO_FAR
            still -> LiveStatus.READING
            else -> LiveStatus.HOLD_STILL
        }
        _state.value = LiveFrame(detection.outline, status, voter.count)
        if (manual && voter.count > 0) {
            capture(timeMillis)
            return FrameAction.NONE
        }
        return when {
            still || manual -> FrameAction.READ
            near -> FrameAction.INK
            else -> FrameAction.NONE
        }
    }

    /** Tinta de un fotograma que [onFrame] pidió solo segmentar (`toInkMarks`). */
    fun onInk(marked: List<Boolean>) {
        if (!captured) voter.addInk(marked)
    }

    /** Si el tablero se movió o se perdió, el periodo quieto es otro y lo votado ya no vale. */
    private fun restartVoteIfMoved() {
        val since = stability.stillSince
        if (since != votingSince) {
            voter.reset()
            votingSince = since
        }
    }

    /**
     * Lectura del fotograma que pidió [onFrame]. Al completar la votación, el tablero sale por
     * [captures] y se devuelve; si no, `null`.
     */
    fun onReading(cells: List<CellReading>, timeMillis: Long): ScannedBoard? {
        if (captured) return null
        voter.add(cells)
        _state.value = _state.value.copy(reads = voter.count)
        if (!voter.complete && !manual) return null
        return capture(timeMillis)
    }

    private fun capture(timeMillis: Long): ScannedBoard {
        captured = true
        manual = false
        val millis = timeMillis - (stability.stillSince ?: timeMillis)
        return voter.result().toScannedBoard(millis).also { _captures.tryEmit(it) }
    }

    /** Lecturas votadas hasta ahora (para el registro de debug). */
    val reads: Int get() = voter.count

    /** La cámara se ha soltado: no queda ningún tablero a la vista y se puede volver a capturar. */
    fun reset() {
        missed = 0
        captured = false
        manual = false
        stability.reset()
        voter.reset()
        votingSince = null
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
