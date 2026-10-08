package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Step
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

class NakedSubsetsTest {

    private val row1 = (0..8).map { cell(0, it) }
    private val box1 = listOf(cell(0, 0), cell(0, 1), cell(0, 2), cell(1, 0), cell(1, 1), cell(1, 2), cell(2, 0), cell(2, 1), cell(2, 2))

    /** Eliminaciones esperadas: cada dígito en cada celda de [cells]. */
    private fun expected(cells: List<Int>, vararg digits: Int) = cells.flatMap { c -> digits.map { c to it } }.toSet()

    @Test
    fun `naked pair en una fila con celdas en cajas distintas`() {
        val grid = openGrid().restrict(cell(0, 0), 1, 2).restrict(cell(0, 4), 1, 2)
        val step = assertNotNull(NakedPair.find(grid))

        assertEquals(TechniqueId.NAKED_PAIR, step.technique)
        assertEquals(listOf(cell(0, 0), cell(0, 4)), step.highlight)
        assertEquals(listOf(1, 2), step.digits)
        assertEquals(listOf(0), step.units)
        assertEquals(expected(row1 - setOf(cell(0, 0), cell(0, 4)), 1, 2), step.eliminated())
        assertNull(LockedPair.find(grid))
    }

    @Test
    fun `naked triple sin ninguna celda con los tres candidatos`() {
        val pattern = listOf(cell(0, 0), cell(0, 4), cell(0, 8))
        val grid = openGrid().restrict(pattern[0], 1, 2).restrict(pattern[1], 2, 3).restrict(pattern[2], 1, 3)
        assertNull(NakedPair.find(grid))
        val step = assertNotNull(NakedTriple.find(grid))

        assertEquals(TechniqueId.NAKED_TRIPLE, step.technique)
        assertEquals(pattern, step.highlight)
        assertEquals(listOf(1, 2, 3), step.digits)
        assertEquals(expected(row1 - pattern.toSet(), 1, 2, 3), step.eliminated())
    }

    @Test
    fun `naked quad en una fila`() {
        val pattern = listOf(cell(0, 0), cell(0, 3), cell(0, 6), cell(0, 8))
        val grid = openGrid()
            .restrict(pattern[0], 1, 2).restrict(pattern[1], 2, 3).restrict(pattern[2], 3, 4).restrict(pattern[3], 1, 4)
        assertNull(NakedTriple.find(grid))
        val step = assertNotNull(NakedQuad.find(grid))

        assertEquals(TechniqueId.NAKED_QUAD, step.technique)
        assertEquals(listOf(1, 2, 3, 4), step.digits)
        assertEquals(expected(row1 - pattern.toSet(), 1, 2, 3, 4), step.eliminated())
    }

    @Test
    fun `locked pair elimina en la caja y en la fila`() {
        val pattern = listOf(cell(0, 0), cell(0, 1))
        val grid = openGrid().restrict(pattern[0], 1, 2).restrict(pattern[1], 1, 2)
        val step = assertNotNull(LockedPair.find(grid))

        assertEquals(TechniqueId.LOCKED_PAIR, step.technique)
        assertEquals(listOf(18, 0), step.units) // caja 1, fila 1
        assertEquals(expected((box1 + row1).distinct() - pattern.toSet(), 1, 2), step.eliminated())
        // El mismo patrón no se informa también como Naked Pair, y el motor lo elige antes que Locked Candidates.
        assertNull(NakedPair.find(grid))
        assertEquals(TechniqueId.LOCKED_PAIR, assertNotNull(HintEngine().nextStep(grid)).technique)
    }

    @Test
    fun `un par en caja y fila que solo elimina dentro de la caja es naked, no locked`() {
        val pattern = listOf(cell(0, 0), cell(0, 1))
        val grid = openGrid().restrict(pattern[0], 1, 2).restrict(pattern[1], 1, 2)
            .without(1, (3..8).map { cell(0, it) }).without(2, (3..8).map { cell(0, it) })
        assertNull(LockedPair.find(grid))
        val step = assertNotNull(NakedPair.find(grid))

        assertEquals(TechniqueId.NAKED_PAIR, step.technique)
        assertEquals(expected(box1 - pattern.toSet(), 1, 2), step.eliminated())
    }

    @Test
    fun `locked triple en la interseccion de caja y fila`() {
        val pattern = listOf(cell(0, 0), cell(0, 1), cell(0, 2))
        val grid = openGrid().restrict(pattern[0], 1, 2).restrict(pattern[1], 2, 3).restrict(pattern[2], 1, 3)
        val step = assertNotNull(LockedTriple.find(grid))

        assertEquals(TechniqueId.LOCKED_TRIPLE, step.technique)
        assertEquals(expected((box1 + row1).distinct() - pattern.toSet(), 1, 2, 3), step.eliminated())
        assertNull(NakedTriple.find(grid))
    }

    @Test
    fun `sin eliminaciones no devuelve paso`() {
        val others = row1 - setOf(cell(0, 0), cell(0, 4))
        val grid = openGrid().restrict(cell(0, 0), 1, 2).restrict(cell(0, 4), 1, 2).without(1, others).without(2, others)
        assertNull(NakedPair.find(grid))
        assertNull(LockedPair.find(grid))
    }

    @Test
    fun `el motor da el naked pair de la maqueta`() {
        // Estado de la pantalla de pista: tras los singles, Naked Pair {1,3} en F2C1 y F8C1 (columna 1).
        // HoDoKu da la misma pista para este estado.
        val grid = Grid.parse("200361879070289564689457231020100090700000000906708103000010045000504082090800010")
        val expectedStep = Step(
            technique = TechniqueId.NAKED_PAIR,
            highlight = listOf(cell(1, 0), cell(7, 0)),
            units = listOf(9),
            digits = listOf(1, 3),
            eliminations = listOf(Elimination(cell(3, 0), 3), Elimination(cell(6, 0), 3), Elimination(cell(8, 0), 3)),
        )
        assertEquals(expectedStep, HintEngine().nextStep(grid))
    }
}
