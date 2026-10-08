package dev.sudokucoach.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ModelTest {

    @Test
    fun `describe resume el paso de forma compacta`() {
        // Naked Pair {1,3} de la maqueta: columna 1 (unidad 9), celdas F2C1 y F8C1.
        val step = Step(
            technique = TechniqueId.NAKED_PAIR,
            highlight = listOf(9, 63),
            units = listOf(9),
            digits = listOf(1, 3),
            eliminations = listOf(Elimination(27, 3), Elimination(54, 3), Elimination(72, 3)),
        )
        assertEquals("NAKED_PAIR {1,3} columna 1 [F2C1, F8C1] → −3 F4C1, −3 F7C1, −3 F9C1", step.describe())
    }

    @Test
    fun `describe incluye la colocacion`() {
        val step = Step(TechniqueId.NAKED_SINGLE, highlight = listOf(0), digits = listOf(4), placement = Placement(0, 4))
        assertEquals("NAKED_SINGLE {4} [F1C1] → F1C1=4", step.describe())
    }

    @Test
    fun `un paso sin colocacion ni eliminaciones no es valido`() {
        assertFailsWith<IllegalArgumentException> { Step(TechniqueId.NAKED_PAIR, highlight = listOf(0, 1)) }
    }

    @Test
    fun `colocaciones y eliminaciones validan sus rangos`() {
        assertFailsWith<IllegalArgumentException> { Placement(81, 1) }
        assertFailsWith<IllegalArgumentException> { Elimination(0, 0) }
    }

    @Test
    fun `las categorias se numeran como niveles desde 1`() {
        assertEquals(1, TechniqueId.FULL_HOUSE.category.level)
        assertEquals(2, TechniqueId.LOCKED_PAIR.category.level)
        assertEquals(3, TechniqueId.NAKED_PAIR.category.level)
    }

    @Test
    fun `los tableros se comparan por contenido`() {
        val text = ".".repeat(80) + "5"
        assertEquals(Grid.parse(text), Grid.parse(text))
        assertEquals(Grid.parse(text).hashCode(), Grid.parse(text).hashCode())

        val withElimination = Grid.parse(text).apply(
            Step(TechniqueId.NAKED_PAIR, highlight = emptyList(), eliminations = listOf(Elimination(0, 1))),
        )
        assertNotEquals(Grid.parse(text), withElimination)
    }

    @Test
    fun `se puede construir un tablero con candidatos explicitos`() {
        val candidates = IntArray(81) { ALL_CANDIDATES }.also { it[0] = (1 shl 1) or (1 shl 3) }
        val grid = Grid(IntArray(81), candidates)
        assertEquals(listOf(1, 3), grid.candidatesOf(0))
    }

    @Test
    fun `una celda colocada no puede tener candidatos`() {
        assertFailsWith<IllegalArgumentException> { Grid(IntArray(81) { if (it == 0) 5 else 0 }, IntArray(81) { ALL_CANDIDATES }) }
    }

    @Test
    fun `cellsWithCandidate devuelve las celdas de la unidad que admiten el digito`() {
        val grid = openGrid().without(5, listOf(cell(0, 0), cell(0, 4)))
        assertEquals((0..8).filter { it != 0 && it != 4 }.map { cell(0, it) }, grid.cellsWithCandidate(0, 5))
        assertEquals(Units.units[9].toList(), grid.cellsWithCandidate(9, 1)) // columna 1, el 1 sigue en todas
    }

    @Test
    fun `un tablero rechaza mascaras con bits fuera de 1 a 9`() {
        assertFailsWith<IllegalArgumentException> { Grid(IntArray(81), IntArray(81) { 1 }) }
        assertFailsWith<IllegalArgumentException> { Grid(IntArray(81) { 10 }, IntArray(81)) }
    }
}
