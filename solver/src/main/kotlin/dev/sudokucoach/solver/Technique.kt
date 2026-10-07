package dev.sudokucoach.solver

/** Una técnica de resolución: busca un paso aplicable en el estado actual. */
interface Technique {
    val name: String

    /** Devuelve el primer paso que encuentre, o `null` si la técnica no aplica. */
    fun find(grid: Grid): Step?
}
