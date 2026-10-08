package dev.sudokucoach.solver

import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Comprueba que [assertStepConsistent] detecta los pasos incorrectos que debe detectar. */
class StepAssertionsTest {

    private val puzzle =
        "530070000" + "600195000" + "098000060" +
        "800060003" + "400803001" + "700020006" +
        "060000280" + "000419005" + "000080079"

    private val grid = Grid.parse(puzzle)
    private val solution = requireNotNull(Backtracker.solve(grid))

    // F1C3 (celda 2): la solución es 4; sus candidatos son {1, 2, 4}.
    private fun step(placement: Placement? = null, vararg eliminations: Elimination) =
        Step(TechniqueId.NAKED_PAIR, highlight = listOf(2), placement = placement, eliminations = eliminations.toList())

    @Test
    fun `acepta un paso correcto`() {
        assertStepConsistent(grid, step(Placement(2, 4)), solution)
        assertStepConsistent(grid, step(null, Elimination(2, 1)), solution)
    }

    @Test
    fun `detecta una colocacion que contradice la solucion`() {
        assertFailsWith<AssertionError> { assertStepConsistent(grid, step(Placement(2, 1)), solution) }
    }

    @Test
    fun `detecta una colocacion en una celda ocupada o sin ese candidato`() {
        assertFailsWith<AssertionError> { assertStepConsistent(grid, step(Placement(0, 5)), solution) }
        assertFailsWith<AssertionError> { assertStepConsistent(grid, step(Placement(2, 9)), solution) }
    }

    @Test
    fun `detecta la eliminacion del digito de la solucion`() {
        assertFailsWith<AssertionError> { assertStepConsistent(grid, step(null, Elimination(2, 4)), solution) }
    }

    @Test
    fun `detecta una eliminacion sin efecto`() {
        assertFailsWith<AssertionError> { assertStepConsistent(grid, step(null, Elimination(2, 9)), solution) }
    }

    @Test
    fun `detecta eliminaciones repetidas`() {
        assertFailsWith<AssertionError> {
            assertStepConsistent(grid, step(null, Elimination(2, 1), Elimination(2, 1)), solution)
        }
    }
}
