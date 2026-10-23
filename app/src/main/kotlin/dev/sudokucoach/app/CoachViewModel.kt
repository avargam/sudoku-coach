package dev.sudokucoach.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.app.review.Validation
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

/** Pantallas de la app (D-18). Cada tarea de la Fase 3 añade la suya. */
sealed interface Route {
    /** Elegir la foto de la galería (tarea 3.3). */
    data object Picking : Route

    /** Revisar y corregir el tablero leído (tarea 3.5). */
    data object Reviewing : Route

    /** Pista con revelado gradual (tarea 3.7). */
    data object Hint : Route
}

/** Estado de la lectura de una foto, que se muestra en la pantalla de elegir foto. */
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
 */
class CoachViewModel(private val scanner: PhotoScanner) : ViewModel() {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Picking)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _review = MutableStateFlow<ReviewState?>(null)
    val review: StateFlow<ReviewState?> = _review.asStateFlow()

    private val _hint = MutableStateFlow<HintState?>(null)
    val hint: StateFlow<HintState?> = _hint.asStateFlow()

    /** Lee la foto elegida; mientras se lee, otras fotos se ignoran. */
    fun onPhotoPicked(photo: String) {
        if (_scanState.value is ScanState.Reading) return
        _scanState.value = ScanState.Reading(photo)
        viewModelScope.launch {
            when (val result = scanner.scan(photo)) {
                is ScanResult.Read -> {
                    _review.value = ReviewState.of(ReviewBoard.from(result.board))
                    _scanState.value = ScanState.Idle
                    backStack.add(Route.Reviewing)
                }
                ScanResult.NoBoard -> _scanState.value = ScanState.Failed(ScanFailure.NO_BOARD)
                is ScanResult.Failed -> _scanState.value = ScanState.Failed(ScanFailure.UNREADABLE)
            }
        }
    }

    fun selectCell(cell: Int) {
        _review.value = _review.value?.copy(selected = cell)
    }

    /** Escribe [digit] en la celda seleccionada (0 la borra) y vuelve a validar. */
    fun enterDigit(digit: Int) {
        val state = _review.value ?: return
        val cell = state.selected ?: return
        val board = state.board.edit(cell, digit)
        _review.value = state.copy(board = board, validation = board.validate())
    }

    /** Selecciona la siguiente celda sospechosa (o la anterior), dando la vuelta al llegar al final. */
    fun moveToSuspicious(forward: Boolean) {
        val state = _review.value ?: return
        val cells = state.board.suspicious.sorted().ifEmpty { return }
        val current = state.selected ?: if (forward) -1 else 81
        val next = if (forward) cells.firstOrNull { it > current } ?: cells.first() else cells.lastOrNull { it < current } ?: cells.last()
        _review.value = state.copy(selected = next)
    }

    /**
     * Busca la primera pista y la muestra; no hace nada si la validación no lo permite. Empieza
     * siempre desde el tablero revisado: los pasos aplicados en una pista anterior no se conservan.
     */
    fun showHint() {
        val state = _review.value ?: return
        if (!state.validation.allowsHints) return
        _hint.value = HintState.start(state.board.values)
        backStack.add(Route.Hint)
    }

    fun revealMore() = updateHint { it.revealMore() }

    fun applyStep() = updateHint { it.apply() }

    fun nextHint() = updateHint { it.next() }

    fun revealDigit() = updateHint { it.revealDigit() }

    private fun updateHint(change: (HintState) -> HintState) {
        _hint.value = _hint.value?.let(change)
    }

    /** Vuelve a la pantalla anterior; devuelve `false` si ya no hay ninguna y la app debe cerrarse. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    override fun onCleared() {
        (scanner as? Closeable)?.close()
    }
}
