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
}

/**
 * Reconoce el dígito de una celda ya recortada del tablero rectificado.
 * Las implementaciones viven en el módulo Android `vision` (ML Kit; CNN en LiteRT en la Fase 5),
 * así que esta interfaz solo depende de la API de OpenCV.
 */
interface DigitRecognizer {
    fun recognize(cell: Mat): Prediction
}
