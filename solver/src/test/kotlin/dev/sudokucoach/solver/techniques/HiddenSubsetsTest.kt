package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.cell
import dev.sudokucoach.solver.eliminated
import dev.sudokucoach.solver.openGrid
import dev.sudokucoach.solver.restrict
import dev.sudokucoach.solver.without
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HiddenSubsetsTest {

    private val row1 = (0..8).map { cell(0, it) }

    /** Eliminaciones esperadas: cada dígito en cada celda de [cells]. */
    private fun expected(cells: List<Int>, digits: IntRange) = cells.flatMap { c -> digits.map { c to it } }.toSet()

    @Test
    fun `hidden pair en una fila`() {
        // En la fila 1, el 1 y el 2 solo caben en F1C1 y F1C5, que admiten además todos los demás dígitos.
        val pattern = listOf(cell(0, 0), cell(0, 4))
        val others = row1 - pattern.toSet()
        val grid = openGrid().without(1, others).without(2, others)
        val step = assertNotNull(HiddenPair.find(grid))

        assertEquals(TechniqueId.HIDDEN_PAIR, step.technique)
        assertEquals(pattern, step.highlight)
        assertEquals(listOf(1, 2), step.digits)
        assertEquals(listOf(0), step.units)
        assertEquals(expected(pattern, 3..9), step.eliminated())
        assertEquals(TechniqueId.HIDDEN_PAIR, assertNotNull(HintEngine().nextStep(grid)).technique)
    }

    @Test
    fun `hidden triple sin que ninguna celda tenga los tres digitos`() {
        // El 1 cabe en C1 y C5, el 2 en C5 y C9, el 3 en C1 y C9: juntos ocupan exactamente esas tres celdas.
        val pattern = listOf(cell(0, 0), cell(0, 4), cell(0, 8))
        val others = row1 - pattern.toSet()
        val grid = openGrid().without(1, others + cell(0, 8)).without(2, others + cell(0, 0)).without(3, others + cell(0, 4))
        assertNull(HiddenPair.find(grid))
        val step = assertNotNull(HiddenTriple.find(grid))

        assertEquals(TechniqueId.HIDDEN_TRIPLE, step.technique)
        assertEquals(pattern, step.highlight)
        assertEquals(listOf(1, 2, 3), step.digits)
        assertEquals(expected(pattern, 4..9), step.eliminated())
    }

    @Test
    fun `hidden quad en una fila`() {
        val pattern = listOf(cell(0, 0), cell(0, 3), cell(0, 6), cell(0, 8))
        val others = row1 - pattern.toSet()
        val grid = (1..4).fold(openGrid()) { g, d -> g.without(d, others) }
        assertNull(HiddenTriple.find(grid))
        val step = assertNotNull(HiddenQuad.find(grid))

        assertEquals(TechniqueId.HIDDEN_QUAD, step.technique)
        assertEquals(listOf(1, 2, 3, 4), step.digits)
        assertEquals(expected(pattern, 5..9), step.eliminated())
    }

    @Test
    fun `un subconjunto oculto que ya es desnudo no da un paso vacio`() {
        // F1C1 y F1C5 solo admiten {1,2} y además son las únicas celdas de la fila con el 1 y el 2.
        val pattern = listOf(cell(0, 0), cell(0, 4))
        val others = row1 - pattern.toSet()
        val grid = openGrid().restrict(pattern[0], 1, 2).restrict(pattern[1], 1, 2).without(1, others).without(2, others)
        assertNull(HiddenPair.find(grid))
    }
}
