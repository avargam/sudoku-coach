package dev.sudokucoach.solver

data class Placement(val cell: Int, val digit: Int) {
    init {
        require(cell in 0..80) { "Celda fuera de rango: $cell" }
        require(digit in 1..9) { "Dígito fuera de rango: $digit" }
    }
}

data class Elimination(val cell: Int, val digit: Int) {
    init {
        require(cell in 0..80) { "Celda fuera de rango: $cell" }
        require(digit in 1..9) { "Dígito fuera de rango: $digit" }
    }
}

/**
 * Resultado de aplicar una técnica: lo único que la interfaz necesita para dibujar una pista.
 * No contiene texto para el usuario; la app lo compone a partir de estos datos (D-14).
 *
 * @property technique técnica que justifica el paso.
 * @property highlight celdas que forman el patrón.
 * @property units unidades implicadas (0..26, ver [Units]), p. ej. la columna de un Naked Pair. Cuando hay
 *   varias, la primera es donde está el patrón y la siguiente donde se elimina (p. ej. caja y fila en
 *   un Pointing; fila y caja en un Claiming).
 * @property digits dígitos del patrón, p. ej. {1, 3} en un Naked Pair.
 * @property placement dígito que se puede colocar, si la técnica llega a eso.
 * @property eliminations candidatos que se pueden descartar.
 */
data class Step(
    val technique: TechniqueId,
    val highlight: List<Int>,
    val units: List<Int> = emptyList(),
    val digits: List<Int> = emptyList(),
    val placement: Placement? = null,
    val eliminations: List<Elimination> = emptyList(),
) {
    init {
        require(placement != null || eliminations.isNotEmpty()) { "Un paso debe colocar o eliminar algo" }
        require(units.all { it in 0..26 }) { "Unidad fuera de rango: $units" }
    }

    /**
     * Descripción técnica para depurar y para los mensajes de los tests; no es texto para el usuario.
     * Ejemplo: `NAKED_PAIR {1,3} columna 1 [F2C1, F8C1] → −3 F4C1, −3 F7C1, −3 F9C1`.
     */
    fun describe(): String = buildString {
        append(technique.name)
        if (digits.isNotEmpty()) append(digits.joinToString(",", " {", "}"))
        if (units.isNotEmpty()) append(units.joinToString(", ", " ") { Units.unitName(it) })
        append(highlight.joinToString(", ", " [", "]") { Units.cellName(it) })
        append(" →")
        placement?.let { append(" ${Units.cellName(it.cell)}=${it.digit}") }
        if (eliminations.isNotEmpty()) {
            if (placement != null) append(";")
            append(eliminations.joinToString(", ", " ") { "−${it.digit} ${Units.cellName(it.cell)}" })
        }
    }
}
