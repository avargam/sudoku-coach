package dev.sudokucoach.app.hint

import dev.sudokucoach.solver.Backtracker
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Placement
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Units

/**
 * Una pista en curso (tarea 3.7). Trabaja sobre un [Grid] con candidatos y no solo con valores,
 * porque un paso de eliminación cambia los candidatos sin colocar nada.
 *
 * @property grid tablero de la pista; tras [apply], con el paso ya aplicado.
 * @property step paso que se explica; `null` si el tablero está resuelto ([solved]) o si hacen falta
 *   técnicas que aún no hay (Fase 6).
 * @property level revelado gradual (`03-diseno.md` §5.3): 1 técnica, 2 dónde, 3 qué.
 * @property applied si [step] ya está aplicado en [grid].
 * @property revealed último dígito revelado con la solución, cuando no había técnica.
 */
data class HintState(
    val grid: Grid,
    val step: Step?,
    val level: Int = 1,
    val applied: Boolean = false,
    val revealed: Placement? = null,
) {
    init {
        require(level in 1..MAX_LEVEL) { "Nivel fuera de rango: $level" }
    }

    val solved: Boolean get() = grid.isSolved()

    /** Muestra un nivel más, hasta el 3. */
    fun revealMore(): HintState = if (step == null) this else copy(level = (level + 1).coerceAtMost(MAX_LEVEL))

    /** Aplica el paso al tablero; se sigue viendo la misma pista. */
    fun apply(): HintState = if (step == null || applied) this else copy(grid = grid.apply(step), applied = true, level = MAX_LEVEL)

    /** Siguiente pista, en el nivel 1, aplicando antes el paso actual si no se había hecho. */
    fun next(engine: HintEngine = HintEngine()): HintState {
        val current = if (step != null && !applied) grid.apply(step) else grid
        return start(current, engine)
    }

    /**
     * Sin técnica disponible, coloca un dígito de la solución: el de la celda vacía con menos
     * candidatos, que es el más fácil de comprobar a mano. Después busca la siguiente pista.
     */
    fun revealDigit(engine: HintEngine = HintEngine()): HintState {
        if (step != null || solved) return this
        val solution = Backtracker.solve(grid) ?: return this
        val cell = (0 until 81).filter { grid.isEmpty(it) }.minBy { Integer.bitCount(grid.candidates[it]) }
        val placement = Placement(cell, solution[cell])
        return start(grid.place(placement), engine).copy(revealed = placement)
    }

    companion object {
        const val MAX_LEVEL = 3

        /** Primera pista para estos valores. */
        fun start(values: IntArray, engine: HintEngine = HintEngine()) = start(Grid.fromValues(values), engine)

        fun start(grid: Grid, engine: HintEngine = HintEngine()) = HintState(grid, engine.nextStep(grid))
    }
}

/** Coloca un dígito y lo quita de los candidatos de las celdas que ven a la suya, como `Grid.apply`. */
private fun Grid.place(placement: Placement): Grid {
    val values = values.copyOf().also { it[placement.cell] = placement.digit }
    val candidates = candidates.copyOf().also { it[placement.cell] = 0 }
    for (peer in Units.peers[placement.cell]) candidates[peer] = candidates[peer] and (1 shl placement.digit).inv()
    return Grid(values, candidates)
}
