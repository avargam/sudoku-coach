package dev.sudokucoach.app.ui.board

import androidx.compose.runtime.Immutable

/** De dónde viene un dígito; decide el peso de la letra (D-12). */
enum class DigitOrigin {
    /** Impreso en el papel: negrita. */
    GIVEN,

    /** Escrito por el usuario: regular. */
    USER,

    /** Sin clasificar (p. ej. añadido en la revisión): regular. */
    UNKNOWN,
}

/** Lo que una pantalla quiere señalar en una celda. Una celda puede tener varias. */
enum class CellDecoration {
    /** Celda seleccionada para editar: relleno `accentSoft`. */
    SELECTED,

    /** Parte del patrón de la pista: relleno `accentSoft`. */
    PATTERN,

    /** Celda donde la pista coloca un dígito: contorno `accent` continuo. */
    PLACEMENT,

    /** Lectura dudosa que conviene revisar: contorno `accent` punteado. */
    SUSPICIOUS,

    /** Dígito que contradice la solución (D-19): contorno `accent` continuo y dígito en `accent`. */
    ERROR,
}

/**
 * Una celda tal como se dibuja.
 *
 * @property value dígito (1..9) o 0 si está vacía.
 * @property placement dígito que la pista coloca en una celda vacía; se dibuja en `accent`.
 * @property candidates candidatos que se muestran en la mini-rejilla 3×3 (solo en celdas vacías).
 * @property eliminated candidatos que la pista descarta; se dibujan tachados en `inkMuted`.
 */
@Immutable
data class CellUi(
    val value: Int = 0,
    val origin: DigitOrigin = DigitOrigin.UNKNOWN,
    val decorations: Set<CellDecoration> = emptySet(),
    val placement: Int? = null,
    val candidates: Set<Int> = emptySet(),
    val eliminated: Set<Int> = emptySet(),
) {
    init {
        require(value in 0..9) { "Dígito fuera de rango: $value" }
        require(placement == null || (value == 0 && placement in 1..9)) { "Colocación inválida: $placement en $value" }
        require(eliminated.all { it in candidates }) { "Eliminados $eliminated fuera de los candidatos $candidates" }
    }
}

/**
 * El tablero que dibuja [SudokuBoard]: 81 celdas en orden de filas, como en el `solver`. No sabe
 * nada de técnicas; cada pantalla traduce su estado a decoraciones (tarea 3.2).
 */
@Immutable
data class BoardUi(val cells: List<CellUi>) {
    init {
        require(cells.size == 81) { "Un tablero tiene 81 celdas, no ${cells.size}" }
    }

    companion object {
        /** Tablero a partir de 81 valores (0 = vacía), todos con el mismo origen. */
        fun of(values: IntArray, origin: DigitOrigin = DigitOrigin.UNKNOWN) =
            BoardUi(values.map { CellUi(value = it, origin = if (it == 0) DigitOrigin.UNKNOWN else origin) })
    }
}
