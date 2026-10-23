package dev.sudokucoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.explain.ExplanationText
import dev.sudokucoach.app.explain.StepExplainer
import dev.sudokucoach.app.ui.board.BoardUi
import dev.sudokucoach.app.ui.board.CellDecoration
import dev.sudokucoach.app.ui.board.SudokuBoard
import dev.sudokucoach.app.ui.theme.SudokuTheme

/**
 * Primera pista, provisional (tareas 3.5 y 3.6): el tablero con el patrón y la colocación, el nivel,
 * el nombre de la técnica y la explicación, todo a la vez. El revelado gradual es la tarea 3.7.
 */
@Composable
fun HintScreen(viewModel: CoachViewModel) {
    val state by viewModel.review.collectAsStateWithLifecycle()
    val review = state ?: return
    val step = viewModel.hint
    val placement = step?.placement
    val cells = boardUi(review.copy(selected = null)).cells.mapIndexed { cell, ui ->
        when {
            step == null -> ui.copy(decorations = emptySet())
            placement?.cell == cell -> ui.copy(decorations = setOf(CellDecoration.PLACEMENT), placement = placement.digit)
            cell in step.highlight -> ui.copy(decorations = setOf(CellDecoration.PATTERN))
            else -> ui.copy(decorations = emptySet())
        }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.hint_title), style = MaterialTheme.typography.titleLarge)
            SudokuBoard(BoardUi(cells), Modifier.fillMaxWidth())
            if (step == null) {
                Text(stringResource(R.string.hint_none), Modifier.testTag("hint_text"))
            } else {
                val resources = LocalContext.current.resources
                val texts = ExplanationText.texts(step.technique)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ExplanationText.level(resources, step.technique.category), color = SudokuTheme.colors.inkMuted, style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(texts.name), Modifier.testTag("hint_text"), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(texts.english), color = SudokuTheme.colors.inkMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text(ExplanationText.explanation(resources, StepExplainer.explain(step)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
