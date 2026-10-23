package dev.sudokucoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ui.board.BoardUi
import dev.sudokucoach.app.ui.board.CellUi
import dev.sudokucoach.app.ui.board.SudokuBoard
import dev.sudokucoach.app.ui.theme.SudokuTheme
import java.util.Locale

/**
 * Tablero leído de la foto (tarea 3.3). Por ahora solo lo muestra; las celdas sospechosas, la
 * validación y la edición llegan en las tareas 3.4 y 3.5.
 */
@Composable
fun ReviewingScreen(viewModel: CoachViewModel) {
    val scanned = viewModel.scanned ?: return
    val board = BoardUi(List(81) { CellUi(value = scanned.values[it], origin = scanned.origins[it]) })
    val digits = scanned.values.count { it != 0 }
    val seconds = String.format(Locale.getDefault(), "%.1f", scanned.millis / 1000.0)
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
            SudokuBoard(board)
            Text(stringResource(R.string.review_summary, digits, seconds), color = SudokuTheme.colors.inkMuted)
        }
    }
}
