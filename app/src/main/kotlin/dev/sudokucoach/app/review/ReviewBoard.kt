package dev.sudokucoach.app.review

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.scan.ScannedBoard
import dev.sudokucoach.solver.Units

/**
 * Tablero que el usuario revisa antes de pedir pistas (tarea 3.4). Inmutable: [edit] devuelve uno nuevo.
 *
 * @property values 81 dígitos por filas; 0 si la celda está vacía o no se pudo leer.
 * @property origins origen de cada dígito; `UNKNOWN` en las vacías y en los dígitos añadidos al revisar.
 * @property confidences confianza de cada lectura; 1 en las celdas editadas.
 * @property inked celdas en las que el segmentador vio tinta, se haya leído o no.
 */
class ReviewBoard(
    val values: IntArray,
    val origins: List<DigitOrigin>,
    val confidences: FloatArray,
    val inked: BooleanArray,
) {
    init {
        require(values.size == 81 && origins.size == 81 && confidences.size == 81 && inked.size == 81) {
            "Un tablero tiene 81 celdas"
        }
        require(values.all { it in 0..9 }) { "Los valores deben estar entre 0 y 9" }
    }

    /** Celdas con un dígito que aparece en otra celda de su fila, columna o caja. */
    val repeated: Set<Int> by lazy {
        (0 until 81).filter { cell -> values[cell] != 0 && Units.peers[cell].any { values[it] == values[cell] } }.toSet()
    }

    /**
     * Celdas que el usuario debería mirar: tinta sin lectura, lectura poco segura (D-08) o dígito
     * repetido en una unidad.
     */
    val suspicious: Set<Int> by lazy {
        (0 until 81).filter { cell ->
            (inked[cell] && values[cell] == 0) || (values[cell] != 0 && confidences[cell] < MIN_CONFIDENCE) || cell in repeated
        }.toSet()
    }

    /**
     * Pone [digit] en [cell] (0 la borra). La celda queda confirmada: confianza 1 y tinta solo si hay
     * dígito. Un dígito nuevo en una celda vacía tiene origen `UNKNOWN`; uno que corrige una lectura
     * conserva el origen, porque el contraste de la tinta sigue siendo válido.
     */
    fun edit(cell: Int, digit: Int): ReviewBoard {
        require(cell in 0 until 81 && digit in 0..9)
        val origin = when {
            digit == 0 -> DigitOrigin.UNKNOWN
            values[cell] == 0 -> DigitOrigin.UNKNOWN
            else -> origins[cell]
        }
        return ReviewBoard(
            values = values.copyOf().also { it[cell] = digit },
            origins = origins.toMutableList().also { it[cell] = origin },
            confidences = confidences.copyOf().also { it[cell] = 1f },
            inked = inked.copyOf().also { it[cell] = digit != 0 },
        )
    }

    fun validate(): Validation = BoardValidator.validate(this)

    companion object {
        /** Por debajo de esta confianza la lectura se pide al usuario (D-08): ninguna por encima falla. */
        const val MIN_CONFIDENCE = 0.5f

        fun from(scanned: ScannedBoard) = ReviewBoard(
            values = scanned.values.copyOf(),
            origins = scanned.origins,
            confidences = scanned.confidences.copyOf(),
            inked = scanned.inked.copyOf(),
        )
    }
}
