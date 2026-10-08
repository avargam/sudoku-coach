package dev.sudokucoach.solver

/** Máscara con los bits 1..9 activos (bit d = candidato d). */
const val ALL_CANDIDATES = 0x3FE

/**
 * Estado inmutable del tablero: valores colocados y máscaras de candidatos.
 * `values[i] == 0` indica celda vacía.
 *
 * Los candidatos se pueden pasar explícitamente (p. ej. para reproducir un estado de HoDoKu con
 * eliminaciones acumuladas) o calcular a partir de los valores con [fromValues].
 */
class Grid(val values: IntArray, val candidates: IntArray) {

    init {
        require(values.size == 81 && candidates.size == 81) { "El tablero debe tener 81 celdas" }
        require(values.all { it in 0..9 }) { "Los valores deben estar entre 0 y 9" }
        require(candidates.all { it and ALL_CANDIDATES.inv() == 0 }) { "Las máscaras solo admiten los bits 1..9" }
        // Invariante: una celda colocada no tiene candidatos, así que basta con hasCandidate para saber
        // si un dígito es posible en una celda vacía.
        require((0 until 81).all { values[it] == 0 || candidates[it] == 0 }) { "Una celda colocada no puede tener candidatos" }
    }

    fun isEmpty(cell: Int) = values[cell] == 0

    fun hasCandidate(cell: Int, digit: Int) = candidates[cell] and (1 shl digit) != 0

    fun isSolved() = values.all { it != 0 }

    /** Candidatos de una celda en orden ascendente. */
    fun candidatesOf(cell: Int): List<Int> = (1..9).filter { hasCandidate(cell, it) }

    /** Celdas de la unidad [unit] (0..26) que todavía admiten [digit]; por el invariante, todas vacías. */
    fun cellsWithCandidate(unit: Int, digit: Int): List<Int> = Units.units[unit].filter { hasCandidate(it, digit) }

    /** Aplica un paso y devuelve un tablero nuevo. */
    fun apply(step: Step): Grid {
        val v = values.copyOf()
        val c = candidates.copyOf()
        step.placement?.let { p ->
            v[p.cell] = p.digit
            c[p.cell] = 0
            for (peer in Units.peers[p.cell]) c[peer] = c[peer] and (1 shl p.digit).inv()
        }
        for (e in step.eliminations) c[e.cell] = c[e.cell] and (1 shl e.digit).inv()
        return Grid(v, c)
    }

    /** Representación de 81 caracteres ('.' = vacía). */
    fun toText(): String = values.joinToString("") { if (it == 0) "." else it.toString() }

    override fun equals(other: Any?): Boolean =
        other is Grid && values.contentEquals(other.values) && candidates.contentEquals(other.candidates)

    override fun hashCode(): Int = 31 * values.contentHashCode() + candidates.contentHashCode()

    override fun toString(): String = toText()

    companion object {
        /** Crea un tablero a partir de 81 caracteres; '0', '.' o '_' son celdas vacías. */
        fun parse(text: String): Grid {
            val clean = text.filter { !it.isWhitespace() }
            require(clean.length == 81) { "Se esperaban 81 celdas y hay ${clean.length}" }
            val values = IntArray(81) { i ->
                when (val ch = clean[i]) {
                    '.', '_', '0' -> 0
                    in '1'..'9' -> ch - '0'
                    else -> throw IllegalArgumentException("Carácter inválido '$ch' en la posición $i")
                }
            }
            return fromValues(values)
        }

        /** Calcula los candidatos a partir de los valores colocados. */
        fun fromValues(values: IntArray): Grid {
            val candidates = IntArray(81)
            for (cell in 0 until 81) {
                if (values[cell] != 0) continue
                var mask = ALL_CANDIDATES
                for (peer in Units.peers[cell]) {
                    if (values[peer] != 0) mask = mask and (1 shl values[peer]).inv()
                }
                candidates[cell] = mask
            }
            return Grid(values.copyOf(), candidates)
        }
    }
}
