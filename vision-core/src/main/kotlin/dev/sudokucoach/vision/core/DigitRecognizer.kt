package dev.sudokucoach.vision.core

import org.opencv.core.Mat

/**
 * Resultado de reconocer una celda.
 *
 * @property digit dígito reconocido (1..9), o 0 si la celda se considera vacía.
 * @property confidence confianza del reconocedor entre 0 y 1; la app pide confirmación por debajo de un umbral.
 */
data class Prediction(val digit: Int, val confidence: Float) {
    init {
        require(digit in 0..9) { "Dígito fuera de rango: $digit" }
        require(confidence in 0f..1f) { "Confianza fuera de rango: $confidence" }
    }

    companion object {
        /** Celda vacía según el segmentador. */
        val EMPTY = Prediction(digit = 0, confidence = 1f)

        /** Celda con tinta en la que el reconocedor no ha leído ningún dígito. */
        val UNREADABLE = Prediction(digit = 0, confidence = 0f)
    }
}

/**
 * Reconoce el dígito de una celda ya recortada del tablero rectificado.
 * Las implementaciones viven en el módulo Android `vision` (ML Kit; CNN en LiteRT en la Fase 5),
 * así que esta interfaz solo depende de la API de OpenCV.
 */
interface DigitRecognizer {
    fun recognize(cell: Mat): Prediction
}

/**
 * Reconoce todos los dígitos del tablero rectificado a partir de su segmentación: es el punto de
 * sustitución del reconocedor (D-08). Existe además de [DigitRecognizer] porque la estrategia que
 * mejor funciona con ML Kit lee el tablero entero de una vez (tarea 2.8).
 */
fun interface BoardRecognizer {
    /** Una predicción por celda, en el orden de [cells]; las vacías deben quedar como [Prediction.EMPTY]. */
    fun recognize(rectified: Mat, cells: List<Cell>): List<Prediction>

    companion object {
        /** Reconoce celda a celda con [recognizer]; las celdas vacías no llegan a él. */
        fun cellByCell(recognizer: DigitRecognizer) = BoardRecognizer { _, cells ->
            cells.map { cell ->
                val digit = cell.digit
                if (cell.empty || digit == null) Prediction.EMPTY else recognizer.recognize(digit)
            }
        }
    }
}
