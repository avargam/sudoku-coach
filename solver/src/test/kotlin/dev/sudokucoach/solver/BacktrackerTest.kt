package dev.sudokucoach.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BacktrackerTest {

    private val puzzle =
        "530070000" + "600195000" + "098000060" +
        "800060003" + "400803001" + "700020006" +
        "060000280" + "000419005" + "000080079"

    private val solution =
        "534678912" + "672195348" + "198342567" +
        "859761423" + "426853791" + "713924856" +
        "961537284" + "287419635" + "345286179"

    // Puzzle de 17 pistas, muy poco restringido: obliga a explorar bastante.
    private val hard = "000000010400000000020000000000050407008000300001090000300400200050100000000806000"

    @Test
    fun `resuelve un puzzle con solucion unica`() {
        val grid = Grid.parse(puzzle)
        val values = assertNotNull(Backtracker.solve(grid))
        assertEquals(solution, values.joinToString(""))
        assertEquals(1, Backtracker.countSolutions(grid))
        assertTrue(Backtracker.hasUniqueSolution(grid))
    }

    @Test
    fun `la solucion respeta las pistas y las reglas`() {
        val grid = Grid.parse(hard)
        val values = assertNotNull(Backtracker.solve(grid))
        for (cell in 0 until 81) {
            if (grid.values[cell] != 0) assertEquals(grid.values[cell], values[cell])
        }
        for (unit in Units.units) {
            assertEquals((1..9).toSet(), unit.map { values[it] }.toSet())
        }
        assertTrue(Backtracker.hasUniqueSolution(grid))
    }

    @Test
    fun `un tablero vacio tiene varias soluciones`() {
        val empty = Grid.parse(".".repeat(81))
        assertEquals(2, Backtracker.countSolutions(empty))
        assertEquals(1, Backtracker.countSolutions(empty, limit = 1))
        assertEquals(5, Backtracker.countSolutions(empty, limit = 5))
        assertFalse(Backtracker.hasUniqueSolution(empty))
    }

    @Test
    fun `un tablero con digitos repetidos no tiene solucion`() {
        val duplicated = Grid.parse("11" + ".".repeat(79))
        assertNull(Backtracker.solve(duplicated))
        assertEquals(0, Backtracker.countSolutions(duplicated))
    }

    @Test
    fun `un tablero sin repetidos pero contradictorio no tiene solucion`() {
        // F1C1 no admite nada: la fila ya tiene 1..8 y la columna tiene el 9.
        val blocked = Grid.parse(".12345678" + "9........" + ".".repeat(63))
        assertNull(Backtracker.solve(blocked))
        assertEquals(0, Backtracker.countSolutions(blocked))
    }

    @Test
    fun `ignora los candidatos y no modifica el tablero`() {
        val grid = Grid.parse(puzzle)
        // Elimina de F1C3 su candidato correcto (4): el backtracking debe encontrar la solución igual.
        val wrong = grid.apply(Step(TechniqueId.NAKED_PAIR, highlight = emptyList(), eliminations = listOf(Elimination(2, 4))))
        val before = wrong.values.copyOf()
        assertEquals(solution, assertNotNull(Backtracker.solve(wrong)).joinToString(""))
        assertTrue(before.contentEquals(wrong.values))
    }
}
