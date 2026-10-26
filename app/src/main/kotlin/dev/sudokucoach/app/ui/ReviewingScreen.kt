package dev.sudokucoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ReviewState
import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.app.review.Validation
import dev.sudokucoach.app.ui.board.BoardUi
import dev.sudokucoach.app.ui.board.CellDecoration
import dev.sudokucoach.app.ui.board.CellUi
import dev.sudokucoach.app.ui.board.SudokuBoard
import dev.sudokucoach.app.ui.theme.SudokuTheme

/**
 * Revisión del tablero leído (tarea 3.5, `03-diseno.md` §5.1): aviso con las celdas por revisar,
 * tablero editable, la celda seleccionada en una línea, teclado fijo y "Ver pista" según D-19.
 * A diferencia de la maqueta, los impresos también se pueden editar: pueden estar mal leídos.
 */
@Composable
fun ReviewingScreen(viewModel: CoachViewModel, onClose: () -> Unit) {
    val state by viewModel.review.collectAsStateWithLifecycle()
    val review = state ?: return
    ReviewingContent(
        review = review,
        onClose = onClose,
        onCellClick = viewModel::selectCell,
        onDigit = viewModel::enterDigit,
        onMove = viewModel::moveToSuspicious,
        onShowHint = viewModel::showHint,
    )
}

@Composable
fun ReviewingContent(
    review: ReviewState,
    onClose: () -> Unit,
    onCellClick: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    onMove: (forward: Boolean) -> Unit,
    onShowHint: () -> Unit,
) {
    val colors = SudokuTheme.colors
    // La validación y "Ver pista" quedan fijos abajo; si la pantalla es baja, se desplaza lo de arriba.
    Scaffold(
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp, 4.dp, 16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(validationText(review.validation), Modifier.testTag("review_validation"), color = colors.inkMuted, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = onShowHint,
                    enabled = review.validation.allowsHints,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.show_hint), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.height(48.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(StrokeIcons.Close, contentDescription = stringResource(R.string.review_close), tint = colors.ink)
                }
                Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
            }
            FlagsBar(review.board.suspicious.size, onMove)
            SudokuBoard(boardUi(review), Modifier.fillMaxWidth(), onCellClick)
            Text(
                cellCaption(review),
                Modifier.heightIn(min = 20.dp).testTag("review_caption"),
                color = colors.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Keypad(onDigit)
        }
    }
}

/** "N celdas por revisar" con ◀ ▶ para saltar entre ellas, o "Nada por revisar". */
@Composable
private fun FlagsBar(count: Int, onMove: (forward: Boolean) -> Unit) {
    val colors = SudokuTheme.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = if (count > 0) pluralStringResource(R.plurals.review_flags, count, count) else stringResource(R.string.review_no_flags)
        Text(text, Modifier.weight(1f).testTag("review_flags"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (count > 0) {
            IconButton(onClick = { onMove(false) }) {
                Icon(StrokeIcons.ChevronLeft, contentDescription = stringResource(R.string.review_previous_flag), tint = colors.ink)
            }
            IconButton(onClick = { onMove(true) }) {
                Icon(StrokeIcons.ChevronRight, contentDescription = stringResource(R.string.review_next_flag), tint = colors.ink)
            }
        }
    }
}

/** Teclado fijo de 5 × 2: 1–9 y borrar. Teclas de 48 dp, el mínimo táctil, para que todo quepa en el A57. */
@Composable
private fun Keypad(onDigit: (Int) -> Unit) {
    val colors = SudokuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(1..5, 6..10)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in row) {
                    val digit = if (key == 10) 0 else key
                    val description = if (digit == 0) stringResource(R.string.key_erase) else stringResource(R.string.key_digit, digit)
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                            .keyClick(description) { onDigit(digit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit == 0) {
                            Icon(StrokeIcons.Erase, contentDescription = null, Modifier.size(24.dp), tint = colors.ink)
                        } else {
                            Text("$digit", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = colors.ink)
                        }
                    }
                }
            }
        }
    }
}

/** Botón accesible con su descripción, sin el aspecto de `Button` de Material. */
private fun Modifier.keyClick(description: String, onClick: () -> Unit): Modifier =
    semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick)

/** Decoraciones de la revisión: seleccionada, sospechosa y posible error del usuario (D-19). */
fun boardUi(review: ReviewState): BoardUi {
    val board = review.board
    val errors = (review.validation as? Validation.UserErrors)?.cells.orEmpty()
    return BoardUi(
        List(81) { cell ->
            val decorations = buildSet {
                if (cell == review.selected) add(CellDecoration.SELECTED)
                if (cell in errors) add(CellDecoration.ERROR) else if (cell in board.suspicious) add(CellDecoration.SUSPICIOUS)
            }
            CellUi(value = board.values[cell], origin = board.origins[cell], decorations = decorations)
        },
    )
}

@Composable
private fun cellCaption(review: ReviewState): String {
    val cell = review.selected ?: return stringResource(R.string.review_no_selection)
    val board = review.board
    val name = "F${cell / 9 + 1}C${cell % 9 + 1}"
    val value = board.values[cell]
    val errors = (review.validation as? Validation.UserErrors)?.cells.orEmpty()
    return when {
        cell in errors -> stringResource(R.string.review_cell_user_error, name, value)
        cell in board.repeated -> stringResource(R.string.review_cell_repeated, name, value)
        value == 0 && board.inked[cell] -> stringResource(R.string.review_cell_unread, name)
        board.unsure(cell) -> stringResource(R.string.review_cell_low_confidence, name)
        value == 0 -> stringResource(R.string.review_cell_empty, name)
        else -> when (board.origins[cell]) {
            DigitOrigin.GIVEN -> stringResource(R.string.review_cell_given, name)
            DigitOrigin.USER -> stringResource(R.string.review_cell_user, name)
            DigitOrigin.UNKNOWN -> stringResource(R.string.review_cell_unknown, name)
        }
    }
}

@Composable
private fun validationText(validation: Validation): String = when (validation) {
    Validation.Ok -> stringResource(R.string.validation_ok)
    Validation.MultipleSolutions -> stringResource(R.string.validation_multiple)
    is Validation.Repeated -> stringResource(R.string.validation_repeated)
    is Validation.UserErrors -> pluralStringResource(R.plurals.validation_user_errors, validation.cells.size, validation.cells.size)
    Validation.Unsolvable -> stringResource(R.string.validation_unsolvable)
}
