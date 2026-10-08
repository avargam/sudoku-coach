package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Technique
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units

/** N dígitos que en una unidad solo caben en las mismas N celdas: esas celdas pierden sus demás candidatos. */
object HiddenPair : Technique by HiddenSubset(TechniqueId.HIDDEN_PAIR, size = 2)
object HiddenTriple : Technique by HiddenSubset(TechniqueId.HIDDEN_TRIPLE, size = 3)
object HiddenQuad : Technique by HiddenSubset(TechniqueId.HIDDEN_QUAD, size = 4)

/**
 * Búsqueda común de subconjuntos ocultos de tamaño [size], con la semántica de HoDoKu:
 * - la unidad tiene más de [size] celdas vacías (si no, no hay nada "oculto");
 * - se combinan dígitos que aparecen en entre 1 y [size] celdas de la unidad, y cuyas posiciones
 *   juntas ocupan exactamente [size] celdas;
 * - solo se elimina dentro de esas celdas, nunca fuera.
 *
 * Se recorren primero las cajas, luego las filas y luego las columnas, como HoDoKu.
 */
private class HiddenSubset(override val id: TechniqueId, private val size: Int) : Technique {

    override fun find(grid: Grid): Step? {
        for (unit in SEARCH_ORDER) {
            if (Units.units[unit].count { grid.isEmpty(it) } <= size) continue
            // Para cada dígito elegible, las celdas de la unidad que lo admiten.
            val positions = (1..9).associateWith { grid.cellsWithCandidate(unit, it) }
                .filterValues { it.size in 1..size }
            if (positions.size < size) continue
            search(grid, unit, positions, positions.keys.toList(), start = 0, chosen = mutableListOf(), cells = emptySet())
                ?.let { return it }
        }
        return null
    }

    /** Recorre las combinaciones de [size] dígitos descartando pronto las que ya ocupan más de [size] celdas. */
    private fun search(
        grid: Grid,
        unit: Int,
        positions: Map<Int, List<Int>>,
        digits: List<Int>,
        start: Int,
        chosen: MutableList<Int>,
        cells: Set<Int>,
    ): Step? {
        if (chosen.size == size) {
            return if (cells.size == size) buildStep(grid, unit, chosen, cells) else null
        }
        for (i in start until digits.size) {
            val union = cells + positions.getValue(digits[i])
            if (union.size > size) continue
            chosen += digits[i]
            val found = search(grid, unit, positions, digits, i + 1, chosen, union)
            chosen.removeAt(chosen.lastIndex)
            if (found != null) return found
        }
        return null
    }

    /** Paso que deja en [cells] solo los dígitos [digits], o `null` si no tenían otros candidatos. */
    private fun buildStep(grid: Grid, unit: Int, digits: List<Int>, cells: Set<Int>): Step? {
        val ordered = Units.units[unit].filter { it in cells }
        val eliminations = ordered.flatMap { cell ->
            grid.candidatesOf(cell).filter { it !in digits }.map { Elimination(cell, it) }
        }
        if (eliminations.isEmpty()) return null
        return Step(
            technique = id,
            highlight = ordered,
            units = listOf(unit),
            digits = digits.toList(),
            eliminations = eliminations,
        )
    }

    private companion object {
        val SEARCH_ORDER = (18..26) + (0..8) + (9..17)
    }
}
