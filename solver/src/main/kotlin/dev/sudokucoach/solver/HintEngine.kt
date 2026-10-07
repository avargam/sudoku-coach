package dev.sudokucoach.solver

import dev.sudokucoach.solver.techniques.HiddenSingle
import dev.sudokucoach.solver.techniques.NakedSingle

/**
 * Recorre las técnicas por orden de dificultad y devuelve el primer paso que aplique.
 * El orden de la lista es el orden de las pistas: las técnicas más simples van primero.
 */
class HintEngine(private val techniques: List<Technique> = DEFAULT_TECHNIQUES) {

    fun nextStep(grid: Grid): Step? {
        if (grid.isSolved()) return null
        return techniques.firstNotNullOfOrNull { it.find(grid) }
    }

    /** Aplica pasos hasta resolver el tablero o hasta que ninguna técnica aplique. */
    fun solveLogically(grid: Grid): List<Step> {
        val steps = mutableListOf<Step>()
        var current = grid
        while (true) {
            val step = nextStep(current) ?: break
            steps += step
            current = current.apply(step)
        }
        return steps
    }

    companion object {
        val DEFAULT_TECHNIQUES: List<Technique> = listOf(NakedSingle, HiddenSingle)
    }
}
