package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Placement
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Technique
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units

/** La única celda vacía de una fila, columna o caja: solo puede llevar el dígito que falta. */
object FullHouse : Technique {
    override val id = TechniqueId.FULL_HOUSE

    override fun find(grid: Grid): Step? {
        for ((unitIndex, unit) in Units.units.withIndex()) {
            val empty = unit.filter { grid.isEmpty(it) }
            if (empty.size != 1) continue
            val cell = empty.single()
            // En un tablero contradictorio (un dígito repetido) puede faltar más de un dígito, o el que falta
            // puede no ser candidato: no es una Full House válida y no debe romper el motor.
            val missing = (1..9).singleOrNull { digit -> unit.none { grid.values[it] == digit } } ?: continue
            if (!grid.hasCandidate(cell, missing)) continue
            return Step(
                technique = id,
                highlight = listOf(cell),
                units = listOf(unitIndex),
                digits = listOf(missing),
                placement = Placement(cell, missing),
            )
        }
        return null
    }
}
