package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Technique
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units

/**
 * Locked Candidates tipo 1 (Pointing): dentro de una caja, un dígito solo aparece en una fila
 * o columna, así que se elimina del resto de esa fila o columna.
 */
object LockedCandidatesPointing : Technique {
    override val id = TechniqueId.LOCKED_CANDIDATES_POINTING

    override fun find(grid: Grid): Step? {
        for (box in 18..26) {
            for (digit in 1..9) {
                val cells = grid.cellsWithCandidate(box, digit)
                if (cells.size < 2) continue
                val line = sharedLine(cells) ?: continue
                lockedStep(grid, id, digit, cells, base = box, cover = line)?.let { return it }
            }
        }
        return null
    }

    /** Fila o columna (como índice de unidad) que contiene todas las celdas, si existe. */
    private fun sharedLine(cells: List<Int>): Int? = when {
        cells.all { Units.row(it) == Units.row(cells[0]) } -> Units.rowUnit(cells[0])
        cells.all { Units.col(it) == Units.col(cells[0]) } -> Units.colUnit(cells[0])
        else -> null
    }
}

/**
 * Locked Candidates tipo 2 (Claiming): dentro de una fila o columna, un dígito solo aparece en
 * una caja, así que se elimina del resto de esa caja.
 */
object LockedCandidatesClaiming : Technique {
    override val id = TechniqueId.LOCKED_CANDIDATES_CLAIMING

    override fun find(grid: Grid): Step? {
        for (line in 0..17) {
            for (digit in 1..9) {
                val cells = grid.cellsWithCandidate(line, digit)
                if (cells.size < 2) continue
                if (!cells.all { Units.box(it) == Units.box(cells[0]) }) continue
                lockedStep(grid, id, digit, cells, base = line, cover = Units.boxUnit(cells[0]))?.let { return it }
            }
        }
        return null
    }
}

/**
 * Paso común a ambos tipos: el dígito está confinado en la intersección de [base] y [cover],
 * así que se elimina de las demás celdas de [cover]. Devuelve `null` si no hay nada que eliminar.
 */
private fun lockedStep(grid: Grid, id: TechniqueId, digit: Int, cells: List<Int>, base: Int, cover: Int): Step? {
    val eliminations = grid.cellsWithCandidate(cover, digit)
        .filter { it !in cells }
        .map { Elimination(it, digit) }
    if (eliminations.isEmpty()) return null
    return Step(technique = id, highlight = cells, units = listOf(base, cover), digits = listOf(digit), eliminations = eliminations)
}
