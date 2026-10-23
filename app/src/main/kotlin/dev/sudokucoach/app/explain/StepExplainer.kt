package dev.sudokucoach.app.explain

import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.TechniqueId

/**
 * Argumento de la plantilla de una explicación. Es estructurado y sin texto: la interfaz lo
 * convierte con `strings.xml` (D-14), así que todo lo que lee el usuario está en los recursos.
 */
sealed interface ExplanationArg {
    /** Una celda (0..80), que se muestra como "F3C5". */
    data class Cell(val cell: Int) : ExplanationArg

    /** Varias celdas: "F1C4 y F2C6", "F1C1, F1C2 y F1C3". */
    data class Cells(val cells: List<Int>) : ExplanationArg

    /** Una unidad (0..26, ver `Units`): "la fila 3", "la columna 5", "la caja 2". */
    data class House(val unit: Int) : ExplanationArg

    data class Digit(val digit: Int) : ExplanationArg

    /** Varios dígitos: "1 y 3", "1, 3 y 7". */
    data class Digits(val digits: List<Int>) : ExplanationArg

    /**
     * Candidatos descartados, agrupados por dígito en orden ascendente y con las celdas en el orden
     * del paso: "se descarta el 3 en F4C1 y F7C1", "se descartan el 3 en F4C1, y el 5 en F9C1".
     */
    data class Eliminations(val byDigit: List<Pair<Int, List<Int>>>) : ExplanationArg
}

/** Lo que la app necesita para explicar un paso: la técnica elige la plantilla y estos son sus argumentos. */
data class Explanation(val technique: TechniqueId, val args: List<ExplanationArg>)

/**
 * `Step` → [Explanation] (tarea 3.6). El orden de [Explanation.args] es el de los marcadores de la
 * plantilla de cada técnica en `strings.xml` (`%1$s`, `%2$s`…), así que cambiar uno obliga a cambiar
 * el otro; el test instrumentado `ExplanationTextTest` comprueba que coinciden.
 */
object StepExplainer {
    fun explain(step: Step): Explanation {
        val cells = ExplanationArg.Cells(step.highlight)
        val args = when (step.technique) {
            // "F3C5 es la única celda libre de la fila 3, así que lleva el dígito que falta: el 7."
            TechniqueId.FULL_HOUSE -> listOf(ExplanationArg.Cell(step.highlight.single()), house(step, 0), digit(step))
            // "En F3C5 solo cabe el 7…"
            TechniqueId.NAKED_SINGLE -> listOf(ExplanationArg.Cell(step.highlight.single()), digit(step))
            // "En la caja 2, el 7 solo puede ir en F3C5…"
            TechniqueId.HIDDEN_SINGLE -> listOf(house(step, 0), digit(step), ExplanationArg.Cell(step.highlight.single()))
            // "Las celdas F1C1 y F1C2 comparten la caja 1 y la fila 1, y entre las dos solo admiten 1 y 3…"
            TechniqueId.LOCKED_PAIR, TechniqueId.LOCKED_TRIPLE ->
                listOf(cells, house(step, 0), house(step, 1), digits(step), eliminations(step))
            // "En la caja 1 (o la fila 1), el 4 solo puede ir en F1C1 y F1C2, que están en la fila 1 (o la caja 1)…"
            TechniqueId.LOCKED_CANDIDATES_POINTING, TechniqueId.LOCKED_CANDIDATES_CLAIMING ->
                listOf(house(step, 0), digit(step), cells, house(step, 1), eliminations(step))
            // "En la columna 1, las celdas F2C1 y F8C1 solo admiten entre las dos 1 y 3…"
            TechniqueId.NAKED_PAIR, TechniqueId.NAKED_TRIPLE, TechniqueId.NAKED_QUAD ->
                listOf(house(step, 0), cells, digits(step), eliminations(step))
            // "En la fila 4, los dígitos 2 y 6 solo pueden ir en F4C3 y F4C7…"
            TechniqueId.HIDDEN_PAIR, TechniqueId.HIDDEN_TRIPLE, TechniqueId.HIDDEN_QUAD ->
                listOf(house(step, 0), digits(step), cells, eliminations(step))
        }
        return Explanation(step.technique, args)
    }

    private fun house(step: Step, index: Int) = ExplanationArg.House(step.units[index])

    private fun digit(step: Step) = ExplanationArg.Digit(step.digits.single())

    private fun digits(step: Step) = ExplanationArg.Digits(step.digits.sorted())

    private fun eliminations(step: Step) = ExplanationArg.Eliminations(
        step.eliminations.groupBy({ it.digit }, { it.cell }).toSortedMap().map { (digit, cells) -> digit to cells },
    )
}
