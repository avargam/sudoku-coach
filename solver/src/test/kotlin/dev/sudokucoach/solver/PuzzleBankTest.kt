package dev.sudokucoach.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PuzzleBankTest {

    @Test
    fun `cada grupo tiene 100 puzzles sin repetir`() {
        for (group in PuzzleBank.GROUPS) {
            assertEquals(100, PuzzleBank.load(group).size, "Tamaño del grupo $group")
        }
        val all = PuzzleBank.all()
        assertEquals(all.size, all.toSet().size, "Hay puzzles repetidos entre grupos")
    }

    @Test
    fun `todos los puzzles del banco tienen solucion unica`() {
        for (puzzle in PuzzleBank.all()) {
            assertTrue(Backtracker.hasUniqueSolution(Grid.parse(puzzle)), "Sin solución única: $puzzle")
        }
    }
}
