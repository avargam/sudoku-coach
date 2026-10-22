package dev.sudokucoach.app.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sudokucoach.app.ui.theme.SudokuCoachTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Toque y accesibilidad del tablero (tarea 3.2). */
@RunWith(AndroidJUnit4::class)
class SudokuBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val values = IntArray(81).also {
        it[0] = 5 // F1C1 impreso
        it[40] = 7 // F5C5 manuscrito
    }

    private fun board(selected: Int?) = BoardUi(
        List(81) { i ->
            CellUi(
                value = values[i],
                origin = when (i) {
                    0 -> DigitOrigin.GIVEN
                    40 -> DigitOrigin.USER
                    else -> DigitOrigin.UNKNOWN
                },
                decorations = if (i == selected) setOf(CellDecoration.SELECTED) else emptySet(),
            )
        },
    )

    @Test
    fun tocarUnaCeldaLaSelecciona() {
        var clicked: Int? = null
        compose.setContent {
            var selected by remember { mutableStateOf<Int?>(null) }
            SudokuCoachTheme {
                SudokuBoard(board(selected), onCellClick = { clicked = it; selected = it })
            }
        }
        compose.onNodeWithTag("cell-40").assertIsNotSelected()
        compose.onNodeWithTag("cell-40").performClick()
        assertEquals(40, clicked)
        compose.onNodeWithTag("cell-40").assertIsSelected()
    }

    @Test
    fun laSemanticaDescribeCadaCelda() {
        compose.setContent { SudokuCoachTheme { SudokuBoard(board(selected = null)) } }
        compose.onNodeWithContentDescription("Fila 1, columna 1: 5, impreso").assertExists()
        compose.onNodeWithContentDescription("Fila 5, columna 5: 7, manuscrito").assertExists()
        compose.onNodeWithContentDescription("Fila 9, columna 9: vacía").assertExists()
        compose.onAllNodesWithContentDescription("Fila", substring = true).assertCountEquals(81)
    }

    @Test
    fun laSemanticaIncluyeLasMarcasDeLaPista() {
        val cells = board(selected = null).cells.toMutableList()
        cells[2] = CellUi(decorations = setOf(CellDecoration.PATTERN), candidates = setOf(1, 3, 8), eliminated = setOf(8))
        cells[3] = CellUi(placement = 4, decorations = setOf(CellDecoration.PLACEMENT))
        compose.setContent { SudokuCoachTheme { SudokuBoard(BoardUi(cells)) } }
        compose.onNodeWithContentDescription("Fila 1, columna 3: vacía. parte de la pista. candidatos 1, 3. se descarta 8").assertExists()
        compose.onNodeWithContentDescription("Fila 1, columna 4: aquí va un 4").assertExists()
    }
}
