package dev.sudokucoach.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.app.review.Validation
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScannedBoard
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.state.StateCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

/** Pantallas de la app (D-18). */
sealed interface Route {
    /** Nombre con el que se guarda la pila; explícito, porque R8 cambia los nombres de las clases. */
    val name: String

    /** Escáner con la cámara (tarea 4.0) y, como alternativa, la foto de la galería (tarea 3.3). */
    data object Scanning : Route {
        override val name = "scanning"
    }

    /** Revisar y corregir el tablero leído (tarea 3.5). */
    data object Reviewing : Route {
        override val name = "reviewing"
    }

    /** Pista con revelado gradual (tarea 3.7). */
    data object Hint : Route {
        override val name = "hint"
    }

    /** Versión, enlace al código y licencias de terceros (tarea P.6). */
    data object About : Route {
        override val name = "about"
    }
}

/** Estado de la lectura de una foto, que se muestra en el escáner. */
sealed interface ScanState {
    data object Idle : ScanState

    /** Leyendo [photo], que se muestra mientras tanto. */
    data class Reading(val photo: String) : ScanState

    data class Failed(val reason: ScanFailure) : ScanState
}

enum class ScanFailure {
    /** La foto no contiene un tablero reconocible. */
    NO_BOARD,

    /** La foto no se pudo abrir o procesar. */
    UNREADABLE,
}

/**
 * Revisión en curso: el tablero, la celda seleccionada (o ninguna) y su validación (D-19), que se
 * recalcula en cada edición.
 */
data class ReviewState(val board: ReviewBoard, val selected: Int?, val validation: Validation) {
    companion object {
        /** Empieza con la primera celda sospechosa seleccionada, si la hay. */
        fun of(board: ReviewBoard) = ReviewState(board, board.suspicious.minOrNull(), board.validate())
    }
}

/**
 * Estado de la app (D-18): la pila de pantallas vive aquí y no en la interfaz, para que la máquina
 * de estados de `03-diseno.md` §5.2 decida a dónde se va y sobreviva a los cambios de configuración.
 *
 * La máquina de estados son la pila ([backStack]) y el estado de cada pantalla ([scanState],
 * [review], [hint]). Todo menos la lectura en curso se guarda en [saved] en cada cambio, para
 * sobrevivir al cierre del proceso (tarea 3.8): al volver, la app sigue en la misma pantalla. Una
 * lectura interrumpida se pierde y se vuelve al escáner.
 */
class CoachViewModel(
    private val scanner: PhotoScanner,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Scanning)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _review = MutableStateFlow<ReviewState?>(null)
    val review: StateFlow<ReviewState?> = _review.asStateFlow()

    private val _hint = MutableStateFlow<HintState?>(null)
    val hint: StateFlow<HintState?> = _hint.asStateFlow()

    init {
        restore()
    }

    /** Lee la foto elegida; mientras se lee, otras fotos se ignoran. */
    fun onPhotoPicked(photo: String) {
        if (_scanState.value is ScanState.Reading) return
        _scanState.value = ScanState.Reading(photo)
        viewModelScope.launch {
            when (val result = scanner.scan(photo)) {
                is ScanResult.Read -> {
                    _scanState.value = ScanState.Idle
                    startReview(result.board)
                }
                ScanResult.NoBoard -> _scanState.value = ScanState.Failed(ScanFailure.NO_BOARD)
                is ScanResult.Failed -> _scanState.value = ScanState.Failed(ScanFailure.UNREADABLE)
            }
        }
    }

    /**
     * Tablero leído en vivo con la cámara (tarea 4.3). Se ignora si ya se está leyendo una foto o
     * si el escáner ya no es la pantalla visible, porque la captura llega desde el hilo del análisis.
     */
    fun onBoardScanned(board: ScannedBoard) {
        if (_scanState.value is ScanState.Reading || backStack.last() != Route.Scanning) return
        _scanState.value = ScanState.Idle
        startReview(board)
    }

    private fun startReview(board: ScannedBoard) {
        setReview(ReviewState.of(ReviewBoard.from(board)))
        push(Route.Reviewing)
    }

    fun selectCell(cell: Int) {
        setReview(_review.value?.copy(selected = cell))
    }

    /** Escribe [digit] en la celda seleccionada (0 la borra) y vuelve a validar. */
    fun enterDigit(digit: Int) {
        val state = _review.value ?: return
        val cell = state.selected ?: return
        val board = state.board.edit(cell, digit)
        setReview(state.copy(board = board, validation = board.validate()))
    }

    /** Selecciona la siguiente celda sospechosa (o la anterior), dando la vuelta al llegar al final. */
    fun moveToSuspicious(forward: Boolean) {
        val state = _review.value ?: return
        val cells = state.board.suspicious.sorted().ifEmpty { return }
        val current = state.selected ?: if (forward) -1 else 81
        val next = if (forward) cells.firstOrNull { it > current } ?: cells.first() else cells.lastOrNull { it < current } ?: cells.last()
        setReview(state.copy(selected = next))
    }

    /**
     * Busca la primera pista y la muestra; no hace nada si la validación no lo permite. Empieza
     * siempre desde el tablero revisado: los pasos aplicados en una pista anterior no se conservan.
     */
    fun showHint() {
        val state = _review.value ?: return
        if (!state.validation.allowsHints) return
        setHint(HintState.start(state.board.values))
        push(Route.Hint)
    }

    fun revealMore() = updateHint { it.revealMore() }

    fun applyStep() = updateHint { it.apply() }

    fun nextHint() = updateHint { it.next() }

    fun revealDigit() = updateHint { it.revealDigit() }

    private fun updateHint(change: (HintState) -> HintState) {
        setHint(_hint.value?.let(change))
    }

    /** Abre "Acerca de" desde el escáner. */
    fun showAbout() {
        if (backStack.last() == Route.Scanning) push(Route.About)
    }

    /** Vuelve a la pantalla anterior; devuelve `false` si ya no hay ninguna y la app debe cerrarse. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        saveRoutes()
        return true
    }

    private fun push(route: Route) {
        backStack.add(route)
        saveRoutes()
    }

    private fun setReview(state: ReviewState?) {
        _review.value = state
        saved[KEY_REVIEW] = state?.let { StateCodec.encode(it.board) }
        saved[KEY_SELECTED] = state?.selected
    }

    private fun setHint(state: HintState?) {
        _hint.value = state
        saved[KEY_HINT] = state?.let { StateCodec.encode(it) }
    }

    private fun saveRoutes() {
        saved[KEY_ROUTES] = backStack.joinToString(",") { it.name }
    }

    /**
     * Restaura lo guardado. Una pantalla sin su estado (p. ej. por un formato antiguo que ya no se
     * entiende) se descarta junto con las que tiene encima.
     */
    private fun restore() {
        runCatching { saved.get<String>(KEY_REVIEW)?.let { ReviewState.of(StateCodec.decodeReview(it)) } }.getOrNull()?.let { review ->
            _review.value = review.copy(selected = saved.get<Int>(KEY_SELECTED))
        }
        _hint.value = runCatching { saved.get<String>(KEY_HINT)?.let { StateCodec.decodeHint(it) } }.getOrNull()
        val routes = saved.get<String>(KEY_ROUTES)?.split(",")?.mapNotNull { name -> ROUTES.firstOrNull { it.name == name } }.orEmpty()
        for (route in routes.drop(1)) {
            val restorable = when (route) {
                Route.Scanning -> false
                Route.Reviewing -> _review.value != null
                Route.Hint -> _hint.value != null
                Route.About -> true
            }
            if (!restorable) break
            backStack.add(route)
        }
    }

    private companion object {
        const val KEY_ROUTES = "routes"
        const val KEY_REVIEW = "review"
        const val KEY_SELECTED = "review_selected"
        const val KEY_HINT = "hint"
        val ROUTES = listOf(Route.Scanning, Route.Reviewing, Route.Hint, Route.About)
    }

    override fun onCleared() {
        (scanner as? Closeable)?.close()
    }
}
