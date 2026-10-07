package dev.sudokucoach.solver

data class Placement(val cell: Int, val digit: Int)

data class Elimination(val cell: Int, val digit: Int)

/**
 * Resultado de aplicar una técnica: lo único que la interfaz necesita para dibujar una pista.
 *
 * @property technique nombre de la técnica (p. ej. "Naked Single").
 * @property highlight celdas que forman el patrón.
 * @property placement dígito que se puede colocar, si la técnica llega a eso.
 * @property eliminations candidatos que se pueden descartar.
 * @property explanation texto explicativo para el usuario.
 */
data class Step(
    val technique: String,
    val highlight: List<Int>,
    val placement: Placement? = null,
    val eliminations: List<Elimination> = emptyList(),
    val explanation: String,
)
