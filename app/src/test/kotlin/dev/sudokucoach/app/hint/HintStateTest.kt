package dev.sudokucoach.app.hint

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.ui.boardUi
import dev.sudokucoach.app.ui.board.CellDecoration
import dev.sudokucoach.solver.Backtracker
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Units
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pista con revelado gradual (tarea 3.7). */
class HintStateTest {

    private fun bank(group: String): List<String> {
        val dir = File(requireNotNull(System.getProperty("sudokucoach.bank")) { "Falta la propiedad sudokucoach.bank" })
        return File(dir, "$group.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun start(puzzle: String) = HintState.start(Grid.parse(puzzle).values)

    /** Un puzzle del banco cuyo primer paso solo elimina candidatos. */
    private val eliminating: HintState by lazy {
        bank("subsets").asSequence().map { start(it) }.first { it.step?.placement == null && it.step != null }
    }

    @Test
    fun `el revelado sube de nivel hasta el 3`() {
        val state = start(bank("singles").first())
        assertEquals(1, state.level)
        assertEquals(2, state.revealMore().level)
        assertEquals(3, state.revealMore().revealMore().level)
        assertEquals(3, state.revealMore().revealMore().revealMore().level)
    }

    @Test
    fun `aplicar cambia el tablero y sigue en la misma pista`() {
        val state = eliminating
        val step = assertNotNull(state.step)
        val applied = state.apply()
        assertEquals(state.grid.apply(step), applied.grid)
        assertTrue(applied.applied)
        assertEquals(3, applied.level)
        assertSame(step, applied.step)
        assertSame(applied, applied.apply(), "aplicar dos veces no hace nada")
        // Un paso de eliminación no coloca nada: cambian los candidatos, no los valores.
        assertContentEquals(state.grid.values, applied.grid.values)
    }

    @Test
    fun `siguiente aplica el paso si hace falta y empieza en el nivel 1`() {
        val state = eliminating.revealMore()
        val next = state.next()
        val expectedGrid = state.grid.apply(state.step!!)
        assertEquals(HintEngine().nextStep(expectedGrid), next.step)
        assertEquals(1, next.level)
        assertFalse(next.applied)
        // Aplicar primero y luego "Siguiente" da lo mismo.
        assertEquals(next, state.apply().next())
    }

    @Test
    fun `con siguiente se resuelven los puzzles del banco`() {
        for (puzzle in bank("singles").take(3) + bank("intersections").take(3) + bank("subsets").take(3)) {
            var state = start(puzzle)
            var steps = 0
            while (state.step != null) {
                state = state.next()
                steps++
                check(steps < 500) { "Demasiados pasos en $puzzle" }
            }
            assertTrue(state.solved, puzzle)
            assertContentEquals(Backtracker.solve(Grid.parse(puzzle)), state.grid.values, puzzle)
        }
    }

    @Test
    fun `sin tecnica se revela un digito de la solucion`() {
        // Tablero vacío: tiene solución, pero ninguna técnica de la Fase 1 da un paso.
        val state = HintState.start(IntArray(81))
        assertNull(state.step)
        assertFalse(state.solved)
        val revealed = state.revealDigit()
        val placement = assertNotNull(revealed.revealed)
        assertEquals(placement.digit, revealed.grid.values[placement.cell])
        assertTrue(Units.peers[placement.cell].none { !revealed.grid.isEmpty(it) && revealed.grid.values[it] == placement.digit })
        assertEquals(1, revealed.level)
        // La siguiente pista olvida el dígito revelado.
        assertNull(revealed.revealMore().copy(step = null).next().revealed)
    }

    @Test
    fun `revelar elige la celda con menos candidatos`() {
        val puzzle = bank("subsets").first()
        val grid = Grid.parse(puzzle)
        // Se simula que no hay técnica para comprobar la elección de la celda.
        val revealed = HintState(grid, step = null).revealDigit()
        val cell = assertNotNull(revealed.revealed).cell
        val fewest = (0 until 81).filter { grid.isEmpty(it) }.minOf { Integer.bitCount(grid.candidates[it]) }
        assertEquals(fewest, Integer.bitCount(grid.candidates[cell]))
        assertEquals(Backtracker.solve(grid)!![cell], revealed.revealed!!.digit)
    }

    @Test
    fun `tablero resuelto`() {
        val solution = assertNotNull(Backtracker.solve(Grid.parse(bank("singles").first())))
        val state = HintState.start(solution)
        assertTrue(state.solved)
        assertNull(state.step)
        assertSame(state, state.revealDigit())
    }

    @Test
    fun `el tablero de la pista segun el nivel`() {
        val origins = List(81) { DigitOrigin.GIVEN }
        val state = eliminating
        val step = state.step!!
        fun decorated(level: Int, applied: Boolean = false) =
            boardUi(state.copy(level = level, applied = applied, grid = if (applied) state.grid.apply(step) else state.grid), origins).cells

        // Nivel 1: el tablero no cambia.
        assertTrue(decorated(1).all { it.decorations.isEmpty() && it.candidates.isEmpty() })
        // Nivel 2: el patrón relleno y con candidatos; aún nada tachado.
        val level2 = decorated(2)
        assertEquals(step.highlight.toSet(), level2.indices.filter { CellDecoration.PATTERN in level2[it].decorations }.toSet())
        assertTrue(step.highlight.all { level2[it].candidates.isNotEmpty() })
        assertTrue(level2.all { it.eliminated.isEmpty() })
        // Nivel 3: los descartes tachados.
        val level3 = decorated(3)
        for (e in step.eliminations) assertTrue(e.digit in level3[e.cell].eliminated, "${Units.cellName(e.cell)} −${e.digit}")
        // Aplicado: los descartados ya no están entre los candidatos.
        val applied = decorated(3, applied = true)
        for (e in step.eliminations) assertFalse(e.digit in applied[e.cell].candidates)
    }

    @Test
    fun `en un paso que coloca no se muestran los candidatos antes del nivel 3`() {
        val state = start(bank("singles").first())
        val placement = assertNotNull(state.step?.placement)
        val level2 = boardUi(state.revealMore(), emptyList()).cells
        assertTrue(level2.all { it.candidates.isEmpty() && it.placement == null })
        val level3 = boardUi(state.revealMore().revealMore(), emptyList()).cells
        assertEquals(placement.digit, level3[placement.cell].placement)
        assertTrue(CellDecoration.PLACEMENT in level3[placement.cell].decorations)
    }
}
