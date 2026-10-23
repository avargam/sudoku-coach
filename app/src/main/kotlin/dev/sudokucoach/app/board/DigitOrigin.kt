package dev.sudokucoach.app.board

/** De dónde viene un dígito; decide el peso de la letra (D-12). */
enum class DigitOrigin {
    /** Impreso en el papel: negrita. */
    GIVEN,

    /** Escrito por el usuario: regular. */
    USER,

    /** Sin clasificar (p. ej. añadido en la revisión): regular. */
    UNKNOWN,
}
