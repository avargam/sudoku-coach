package dev.sudokucoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.explain.ExplanationText
import dev.sudokucoach.app.explain.StepExplainer
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.ui.board.BoardUi
import dev.sudokucoach.app.ui.board.CellDecoration
import dev.sudokucoach.app.ui.board.CellUi
import dev.sudokucoach.app.ui.board.SudokuBoard
import dev.sudokucoach.app.ui.theme.SudokuTheme

/**
 * Pista con revelado gradual (tarea 3.7, `03-diseno.md` §5.3): el tablero arriba y, debajo, un panel
 * fijo con la técnica y los botones. Es un panel y no un *bottom sheet* arrastrable, como en la
 * maqueta, para que el tablero nunca quede tapado.
 */
@Composable
fun HintScreen(viewModel: CoachViewModel, onBack: () -> Unit) {
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val state = hint ?: return
    HintContent(
        state = state,
        origins = review?.board?.origins.orEmpty(),
        onBack = onBack,
        onMore = viewModel::revealMore,
        onApply = viewModel::applyStep,
        onNext = viewModel::nextHint,
        onRevealDigit = viewModel::revealDigit,
    )
}

@Composable
fun HintContent(
    state: HintState,
    origins: List<DigitOrigin>,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onApply: () -> Unit,
    onNext: () -> Unit,
    onRevealDigit: () -> Unit,
) {
    val colors = SudokuTheme.colors
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.height(48.dp).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(StrokeIcons.Back, contentDescription = stringResource(R.string.hint_back), tint = colors.ink)
                }
                Text(stringResource(R.string.hint_title), style = MaterialTheme.typography.titleLarge)
            }
            SudokuBoard(boardUi(state, origins), Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colors.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Si el texto no cabe, se desplaza; el aviso de paso aplicado y los botones quedan fijos.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        state.step != null -> StepPanel(state)
                        state.solved -> Message(stringResource(R.string.hint_solved_title), stringResource(R.string.hint_solved))
                        else -> Message(stringResource(R.string.hint_none_title), stringResource(R.string.hint_none))
                    }
                }
                if (state.applied) AppliedNote(state)
                Buttons(state, onMore, onApply, onNext, onRevealDigit)
            }
        }
    }
}

@Composable
private fun StepPanel(state: HintState) {
    val step = requireNotNull(state.step)
    val colors = SudokuTheme.colors
    val resources = LocalResources.current
    val texts = ExplanationText.texts(step.technique)
    val explanation = StepExplainer.explain(step)
    state.revealed?.let {
        Text(
            stringResource(R.string.hint_revealed, it.digit, ExplanationText.cellName(resources, it.cell)),
            color = colors.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Stages(state.level)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(ExplanationText.level(resources, step.technique.category), color = colors.inkMuted, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(texts.name), Modifier.testTag("hint_name"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(texts.english), color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
    }
    Text(ExplanationText.stage(resources, explanation, state.level), Modifier.testTag("hint_text"), style = MaterialTheme.typography.bodyLarge)
}

/** "Paso aplicado: …", con lo que ha cambiado en el tablero. */
@Composable
private fun AppliedNote(state: HintState) {
    val step = state.step ?: return
    val resources = LocalResources.current
    val text = step.placement?.let { stringResource(R.string.hint_applied_placement, it.digit, ExplanationText.cellName(resources, it.cell)) }
        ?: pluralStringResource(R.plurals.hint_applied_eliminations, step.eliminations.size, step.eliminations.size)
    Text(text, Modifier.testTag("hint_applied"), color = SudokuTheme.colors.inkMuted, style = MaterialTheme.typography.bodyMedium)
}

/** Las tres etapas del revelado, con las alcanzadas en el acento. */
@Composable
private fun Stages(level: Int) {
    val colors = SudokuTheme.colors
    val labels = listOf(R.string.hint_stage_technique, R.string.hint_stage_where, R.string.hint_stage_what)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (index < level) colors.accent else colors.gridLine))
                Text(
                    stringResource(label),
                    color = if (index + 1 == level) colors.ink else colors.inkMuted,
                    fontWeight = if (index + 1 == level) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Message(title: String, text: String) {
    Text(title, Modifier.testTag("hint_name"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Text(text, Modifier.testTag("hint_text"), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Buttons(state: HintState, onMore: () -> Unit, onApply: () -> Unit, onNext: () -> Unit, onRevealDigit: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val height = Modifier.height(52.dp)
    when {
        state.step == null && !state.solved -> Button(onClick = onRevealDigit, height.fillMaxWidth(), shape = shape) {
            Text(stringResource(R.string.hint_reveal_digit), fontWeight = FontWeight.SemiBold)
        }
        state.step == null -> Unit
        state.level < HintState.MAX_LEVEL -> Button(onClick = onMore, height.fillMaxWidth(), shape = shape) {
            Text(stringResource(if (state.level == 1) R.string.hint_show_where else R.string.hint_show_what), fontWeight = FontWeight.SemiBold)
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onNext, height.weight(1f), shape = shape) {
                Text(stringResource(R.string.hint_next), fontWeight = FontWeight.SemiBold, color = SudokuTheme.colors.ink)
            }
            Button(onClick = onApply, height.weight(1.4f), shape = shape, enabled = !state.applied, colors = ButtonDefaults.buttonColors()) {
                Text(stringResource(R.string.hint_apply), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * El tablero según el nivel: en el 1 no cambia; en el 2 se rellenan las celdas del patrón y, en los
 * pasos que eliminan, aparecen sus candidatos (en los que colocan, verlos daría la respuesta); en el
 * 3 se muestran la colocación y los candidatos descartados, tachados hasta aplicar el paso.
 */
fun boardUi(state: HintState, origins: List<DigitOrigin>): BoardUi {
    val grid = state.grid
    val step = state.step
    val level = if (step == null) 0 else state.level
    val pattern = if (step != null && level >= 2) step.highlight.toSet() else emptySet()
    val placement = step?.placement?.takeIf { level >= 3 }
    val eliminated = if (step != null && level >= 3) step.eliminations.groupBy({ it.cell }, { it.digit }) else emptyMap()
    val candidatesInPattern = step?.placement == null
    return BoardUi(
        List(81) { cell ->
            val inPattern = cell in pattern
            val showCandidates = grid.isEmpty(cell) && ((inPattern && candidatesInPattern) || cell in eliminated)
            val candidates = if (showCandidates) grid.candidatesOf(cell).toSet() else emptySet()
            val isPlacement = placement?.cell == cell
            CellUi(
                value = grid.values[cell],
                origin = if (grid.values[cell] == 0) DigitOrigin.UNKNOWN else origins.getOrElse(cell) { DigitOrigin.UNKNOWN },
                decorations = buildSet {
                    if (inPattern) add(CellDecoration.PATTERN)
                    if (isPlacement) add(CellDecoration.PLACEMENT)
                },
                placement = placement?.digit?.takeIf { isPlacement && grid.isEmpty(cell) },
                candidates = candidates,
                eliminated = if (state.applied) emptySet() else eliminated[cell].orEmpty().toSet() intersect candidates,
            )
        },
    )
}
