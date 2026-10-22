package dev.sudokucoach.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

/** Pantallas de la app (D-18). Cada tarea de la Fase 3 añade la suya. */
sealed interface Route {
    /** Elegir la foto de la galería (tarea 3.3). */
    data object Picking : Route
}

/**
 * Estado de la app (D-18): la pila de pantallas vive aquí y no en la interfaz, para que la máquina
 * de estados de `03-diseno.md` §5.2 decida a dónde se va y sobreviva a los cambios de configuración.
 */
class CoachViewModel : ViewModel() {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Picking)

    /** Vuelve a la pantalla anterior; devuelve `false` si ya no hay ninguna y la app debe cerrarse. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
