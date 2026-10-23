package dev.sudokucoach.app.state

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.solver.Grid
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Estado como texto para sobrevivir al cierre del proceso (tarea 3.8). */
class StateCodecTest {

    private fun bank(group: String): List<String> {
        val dir = File(requireNotNull(System.getProperty("sudokucoach.bank")))
        return File(dir, "$group.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun assertSameBoard(expected: ReviewBoard, actual: ReviewBoard) {
        assertContentEquals(expected.values, actual.values)
        assertEquals(expected.origins, actual.origins)
        assertContentEquals(expected.confidences, actual.confidences)
        assertContentEquals(expected.inked, actual.inked)
    }

    @Test
    fun `la revision va y vuelve`() {
        val values = Grid.parse(bank("singles").first()).values
        val board = ReviewBoard(
            values = values,
            origins = List(81) { if (values[it] == 0) DigitOrigin.UNKNOWN else if (it % 2 == 0) DigitOrigin.GIVEN else DigitOrigin.USER },
            confidences = FloatArray(81) { (it % 101) / 100f },
            inked = BooleanArray(81) { values[it] != 0 || it == 4 },
        )
        assertSameBoard(board, StateCodec.decodeReview(StateCodec.encode(board)))
        // Con una edición, que deja la celda en confianza 1 y origen desconocido.
        val edited = board.edit(values.indexOfFirst { it == 0 }, 5)
        assertSameBoard(edited, StateCodec.decodeReview(StateCodec.encode(edited)))
    }

    @Test
    fun `la pista va y vuelve en cada etapa y con candidatos eliminados`() {
        for (puzzle in bank("subsets").take(5)) {
            // Varios pasos adelante, para que haya candidatos eliminados que no se deducen de los valores.
            var state = HintState.start(Grid.parse(puzzle).values)
            while (state.step != null && state.grid == Grid.fromValues(state.grid.values)) state = state.next()
            assertNotEquals(Grid.fromValues(state.grid.values), state.grid, "hay eliminaciones acumuladas")
            for (variant in listOf(state, state.revealMore(), state.revealMore().revealMore(), state.apply())) {
                assertEquals(variant, StateCodec.decodeHint(StateCodec.encode(variant)), puzzle)
            }
        }
    }

    @Test
    fun `la pista con un digito revelado y el tablero resuelto`() {
        val revealed = HintState.start(IntArray(81)).revealDigit()
        assertEquals(revealed, StateCodec.decodeHint(StateCodec.encode(revealed)))
        var solved = HintState.start(Grid.parse(bank("singles").first()).values)
        while (solved.step != null) solved = solved.next()
        assertEquals(solved, StateCodec.decodeHint(StateCodec.encode(solved)))
    }

    @Test
    fun `un texto mal formado falla`() {
        assertFailsWith<IllegalArgumentException> { StateCodec.decodeReview("123|GU") }
        assertFailsWith<IllegalArgumentException> { StateCodec.decodeHint("0".repeat(81) + "|00|1|0|-") }
    }
}
