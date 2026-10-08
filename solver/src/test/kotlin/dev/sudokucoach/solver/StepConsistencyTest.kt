package dev.sudokucoach.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test de propiedad (tarea 1.4): sobre todo el banco, cada paso del motor es compatible
 * con la solución única y cambia el tablero. Los puzzles que el motor aún no termina se
 * validan hasta donde llega; el criterio de resolver todo el banco es de la tarea 1.10.
 */
class StepConsistencyTest {

    private val engine = HintEngine()

    @Test
    fun `todos los pasos del banco son compatibles con la solucion`() {
        val solvedByGroup = PuzzleBank.GROUPS.associateWith { group ->
            PuzzleBank.load(group).count { puzzle -> walk(puzzle) }
        }
        println("Puzzles resueltos por el motor actual: $solvedByGroup")

        // Con todas las técnicas de la Fase 1, el banco entero se resuelve (como en HoDoKu con las mismas técnicas).
        assertEquals(100, solvedByGroup.getValue("singles"), "El grupo singles debería resolverse entero")
        assertEquals(100, solvedByGroup.getValue("intersections"), "El grupo intersections debería resolverse entero")
        assertEquals(100, solvedByGroup.getValue("subsets"), "El grupo subsets debería resolverse entero")
    }

    /** Recorre el puzzle paso a paso validando cada uno; devuelve si quedó resuelto. */
    private fun walk(puzzle: String): Boolean {
        var grid = Grid.parse(puzzle)
        val solution = assertNotNull(Backtracker.solve(grid), "Sin solución: $puzzle")
        while (true) {
            val step = engine.nextStep(grid) ?: return grid.isSolved()
            assertStepConsistent(grid, step, solution)
            grid = grid.apply(step)
        }
    }
}
