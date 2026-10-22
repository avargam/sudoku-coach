package dev.sudokucoach.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sudokucoach.app.ui.board.BoardUi
import dev.sudokucoach.app.ui.board.CellDecoration
import dev.sudokucoach.app.ui.board.CellUi
import dev.sudokucoach.app.ui.board.DigitOrigin
import dev.sudokucoach.app.ui.board.SudokuBoard
import dev.sudokucoach.app.ui.theme.SudokuCoachTheme
import dev.sudokucoach.app.ui.theme.SudokuTheme

/**
 * Catálogo de decoraciones del tablero, solo en *debug* (tarea 3.2): sustituye a las vistas previas
 * de Compose, que sin Android Studio no hay (D-15). Se revisa en claro y en oscuro cambiando el modo
 * del sistema. Tocar una celda la selecciona.
 */
class BoardCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SudokuCoachTheme {
                var selected by remember { mutableIntStateOf(SELECTED) }
                Scaffold { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Catálogo del tablero", style = MaterialTheme.typography.titleLarge)
                        SudokuBoard(catalogBoard(selected), onCellClick = { selected = it })
                        Text(LEGEND, style = MaterialTheme.typography.bodyMedium, color = SudokuTheme.colors.inkMuted)
                    }
                }
            }
        }
    }

    private companion object {
        /** Sudoku de ejemplo: los dígitos de la primera mitad como impresos y algunos manuscritos. */
        const val GIVENS = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
        val USER = mapOf(2 to 4, 3 to 6, 10 to 7, 18 to 1)
        const val SELECTED = 20
        const val SUSPICIOUS = 10
        const val ERROR = 18
        val PATTERN = listOf(5, 7)
        const val PLACEMENT = 8
        val CANDIDATES = mapOf(5 to setOf(1, 2, 8), 7 to setOf(1, 2), 6 to setOf(1, 2, 4, 8), 8 to setOf(2))
        val ELIMINATED = mapOf(6 to setOf(1, 2))

        const val LEGEND = "F1C6 y F1C8: patrón con candidatos · F1C7: candidatos 1 y 2 eliminados · " +
            "F1C9: colocación · F2C2: sospechosa · F3C1: error · F3C3: seleccionada · " +
            "negrita = impreso, regular = manuscrito"

        fun catalogBoard(selected: Int) = BoardUi(
            List(81) { i ->
                val given = GIVENS[i].digitToIntOrNull() ?: 0
                val user = USER[i] ?: 0
                CellUi(
                    value = if (given != 0) given else user,
                    origin = when {
                        given != 0 -> DigitOrigin.GIVEN
                        user != 0 -> DigitOrigin.USER
                        else -> DigitOrigin.UNKNOWN
                    },
                    decorations = buildSet {
                        if (i == selected) add(CellDecoration.SELECTED)
                        if (i == SUSPICIOUS) add(CellDecoration.SUSPICIOUS)
                        if (i == ERROR) add(CellDecoration.ERROR)
                        if (i in PATTERN) add(CellDecoration.PATTERN)
                        if (i == PLACEMENT) add(CellDecoration.PLACEMENT)
                    },
                    placement = if (i == PLACEMENT) 2 else null,
                    candidates = if (i == PLACEMENT) emptySet() else CANDIDATES[i].orEmpty(),
                    eliminated = ELIMINATED[i].orEmpty(),
                )
            },
        )
    }
}
