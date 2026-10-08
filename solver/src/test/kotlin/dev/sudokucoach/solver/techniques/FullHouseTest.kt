package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Placement
import dev.sudokucoach.solver.TechniqueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FullHouseTest {

    private val solution =
        "534678912" + "672195348" + "198342567" +
        "859761423" + "426853791" + "713924856" +
        "961537284" + "287419635" + "345286179"

    private fun blank(vararg cells: Int) =
        Grid.parse(solution.mapIndexed { i, ch -> if (i in cells) '.' else ch }.joinToString(""))

    @Test
    fun `encuentra la ultima celda vacia de una unidad`() {
        // F5C5 (celda 40) vacía: es la última de su fila, columna y caja; falta el 5.
        val step = assertNotNull(FullHouse.find(blank(40)))
        assertEquals(TechniqueId.FULL_HOUSE, step.technique)
        assertEquals(Placement(40, 5), step.placement)
        assertEquals(listOf(40), step.highlight)
        assertEquals(listOf(5), step.digits)
        assertEquals(listOf(4), step.units) // la fila 5 es la primera unidad que se revisa
    }

    @Test
    fun `se elige antes que un Naked Single presente a la vez`() {
        // Una celda vacía aislada es Full House y Naked Single a la vez: el motor debe nombrar la Full House.
        val step = assertNotNull(HintEngine().nextStep(blank(40)))
        assertEquals(TechniqueId.FULL_HOUSE, step.technique)
    }

    @Test
    fun `un tablero contradictorio no rompe el motor`() {
        // Fila 1 con 8 valores pero el 1 repetido: faltan el 8 y el 9, así que no hay Full House posible.
        val grid = Grid.parse("11234567." + ".".repeat(72))
        assertNull(FullHouse.find(grid))
        HintEngine().nextStep(grid) // no debe lanzar excepciones
    }

    @Test
    fun `no aplica si ninguna unidad tiene una sola celda vacia`() {
        // Bloque 2×2 vacío dentro de la caja 1: dos huecos por fila y columna, cuatro en la caja.
        val grid = blank(0, 1, 9, 10)
        assertNull(FullHouse.find(grid))
        assertEquals(TechniqueId.NAKED_SINGLE, assertNotNull(HintEngine().nextStep(grid)).technique)
    }
}
