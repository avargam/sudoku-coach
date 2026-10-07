package dev.sudokucoach.solver

import dev.sudokucoach.solver.techniques.HiddenSingle
import dev.sudokucoach.solver.techniques.NakedSingle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintEngineTest {

    // Puzzle clásico que se resuelve solo con singles.
    private val puzzle =
        "530070000" + "600195000" + "098000060" +
        "800060003" + "400803001" + "700020006" +
        "060000280" + "000419005" + "000080079"

    private val solution =
        "534678912" + "672195348" + "198342567" +
        "859761423" + "426853791" + "713924856" +
        "961537284" + "287419635" + "345286179"

    @Test
    fun `parse calcula candidatos coherentes con los valores`() {
        val grid = Grid.parse(puzzle)
        // F1C3 (celda 2): ya hay 5,3,7 en la fila; 6,9,8 en la caja/columna -> no puede ser ninguno de ellos.
        for (d in listOf(5, 3, 7, 6, 9, 8)) assertTrue(!grid.hasCandidate(2, d), "no debería admitir $d")
        assertTrue(grid.hasCandidate(2, 4))
    }

    @Test
    fun `parse rechaza longitudes y caracteres invalidos`() {
        assertFailsWith<IllegalArgumentException> { Grid.parse("123") }
        assertFailsWith<IllegalArgumentException> { Grid.parse("x".repeat(81)) }
    }

    @Test
    fun `el primer paso es una colocacion compatible con la solucion`() {
        val step = assertNotNull(HintEngine().nextStep(Grid.parse(puzzle)))
        val placement = assertNotNull(step.placement)
        assertEquals(solution[placement.cell] - '0', placement.digit)
    }

    @Test
    fun `resuelve el puzzle solo con singles y cada paso es correcto`() {
        var grid = Grid.parse(puzzle)
        val engine = HintEngine()
        val steps = engine.solveLogically(grid)

        for (step in steps) {
            val p = assertNotNull(step.placement)
            assertEquals(solution[p.cell] - '0', p.digit, "Paso incorrecto: ${step.describe()}")
            grid = grid.apply(step)
        }
        assertTrue(grid.isSolved())
        assertEquals(solution, grid.toText())
    }

    @Test
    fun `los singles rellenan tecnica, unidad y digitos`() {
        val grid = Grid.parse(puzzle)
        val naked = assertNotNull(NakedSingle.find(grid))
        val nakedPlacement = assertNotNull(naked.placement)
        assertEquals(TechniqueId.NAKED_SINGLE, naked.technique)
        assertEquals(listOf(nakedPlacement.digit), naked.digits)
        assertEquals(listOf(nakedPlacement.cell), naked.highlight)

        val hidden = assertNotNull(HiddenSingle.find(grid))
        val hiddenPlacement = assertNotNull(hidden.placement)
        assertEquals(TechniqueId.HIDDEN_SINGLE, hidden.technique)
        assertEquals(1, hidden.units.size)
        assertTrue(hiddenPlacement.cell in Units.units[hidden.units.single()])
    }

    @Test
    fun `un tablero resuelto no tiene siguiente paso`() {
        assertNull(HintEngine().nextStep(Grid.parse(solution)))
    }
}
