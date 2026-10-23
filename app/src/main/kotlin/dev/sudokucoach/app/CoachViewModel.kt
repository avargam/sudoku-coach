package dev.sudokucoach.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.scan.ScannedBoard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

/** Pantallas de la app (D-18). Cada tarea de la Fase 3 añade la suya. */
sealed interface Route {
    /** Elegir la foto de la galería (tarea 3.3). */
    data object Picking : Route

    /** Revisar el tablero leído (por ahora solo se muestra; la edición llega en 3.5). */
    data object Reviewing : Route
}

/** Estado de la lectura de una foto, que se muestra en la pantalla de elegir foto. */
sealed interface ScanState {
    data object Idle : ScanState

    data object Reading : ScanState

    data class Failed(val reason: ScanFailure) : ScanState
}

enum class ScanFailure {
    /** La foto no contiene un tablero reconocible. */
    NO_BOARD,

    /** La foto no se pudo abrir o procesar. */
    UNREADABLE,
}

/**
 * Estado de la app (D-18): la pila de pantallas vive aquí y no en la interfaz, para que la máquina
 * de estados de `03-diseno.md` §5.2 decida a dónde se va y sobreviva a los cambios de configuración.
 */
class CoachViewModel(private val scanner: PhotoScanner) : ViewModel() {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Picking)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Último tablero leído; lo usa la pantalla de revisión. */
    var scanned: ScannedBoard? = null
        private set

    /** Lee la foto elegida; mientras se lee, otras fotos se ignoran. */
    fun onPhotoPicked(photo: String) {
        if (_scanState.value == ScanState.Reading) return
        _scanState.value = ScanState.Reading
        viewModelScope.launch {
            when (val result = scanner.scan(photo)) {
                is ScanResult.Read -> {
                    scanned = result.board
                    _scanState.value = ScanState.Idle
                    backStack.add(Route.Reviewing)
                }
                ScanResult.NoBoard -> _scanState.value = ScanState.Failed(ScanFailure.NO_BOARD)
                is ScanResult.Failed -> _scanState.value = ScanState.Failed(ScanFailure.UNREADABLE)
            }
        }
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
