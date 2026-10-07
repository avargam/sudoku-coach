package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Placement
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Technique
import dev.sudokucoach.solver.Units

/** Una celda vacía con un único candidato posible. */
object NakedSingle : Technique {
    override val name = "Naked Single"

    override fun find(grid: Grid): Step? {
        for (cell in 0 until 81) {
            if (!grid.isEmpty(cell)) continue
            val mask = grid.candidates[cell]
            if (Integer.bitCount(mask) == 1) {
                val digit = Integer.numberOfTrailingZeros(mask)
                return Step(
                    technique = name,
                    highlight = listOf(cell),
                    placement = Placement(cell, digit),
                    explanation = "En ${Units.cellName(cell)} solo cabe el $digit: " +
                        "los demás dígitos ya aparecen en su fila, columna o caja.",
                )
            }
        }
        return null
    }
}

/** Un dígito que solo puede ir en una celda dentro de una fila, columna o caja. */
object HiddenSingle : Technique {
    override val name = "Hidden Single"

    override fun find(grid: Grid): Step? {
        for ((unitIndex, unit) in Units.units.withIndex()) {
            for (digit in 1..9) {
                var only = -1
                var count = 0
                for (cell in unit) {
                    if (grid.isEmpty(cell) && grid.hasCandidate(cell, digit)) {
                        only = cell
                        count++
                    }
                }
                if (count == 1) {
                    return Step(
                        technique = name,
                        highlight = listOf(only),
                        placement = Placement(only, digit),
                        explanation = "En ${Units.unitName(unitIndex)}, el $digit solo puede ir en " +
                            "${Units.cellName(only)}: es la única celda donde todavía es posible.",
                    )
                }
            }
        }
        return null
    }
}
