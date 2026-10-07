package dev.sudokucoach.solver

/** Máscara con los bits 1..9 activos (bit d = candidato d). */
const val ALL_CANDIDATES = 0x3FE

/**
 * Estado inmutable del tablero: valores colocados y máscaras de candidatos.
 * `values[i] == 0` indica celda vacía.
 */
class Grid(val values: IntArray, val candidates: IntArray) {

    init {
        require(values.size == 81 && candidates.size == 81) { "El tablero debe tener 81 celdas" }
    }

    fun isEmpty(cell: Int) = values[cell] == 0

    fun hasCandidate(cell: Int, digit: Int) = candidates[cell] and (1 shl digit) != 0

    fun isSolved() = values.all { it != 0 }

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
