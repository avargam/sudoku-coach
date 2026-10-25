package dev.sudokucoach.vision.core.live

import dev.sudokucoach.vision.core.BoardReading

/**
 * Lo que se sabe de una celda en una lectura, o en la votación de varias (D-21, tarea 4.3). Solo
 * tipos de Kotlin, sin OpenCV, para votar y guardar lecturas sin imágenes.
 *
 * @property digit 1–9, o 0 si la celda está vacía o no se pudo leer.
 * @property confidence 1 en las vacías, 0 en las celdas con tinta sin lectura.
 * @property inked si el segmentador vio un dígito, se haya leído o no.
 * @property printed si la tinta parece impresa (`Cell.printed`); `false` en las vacías.
 */
data class CellReading(val digit: Int, val confidence: Float, val inked: Boolean, val printed: Boolean) {
    companion object {
        val EMPTY = CellReading(digit = 0, confidence = 1f, inked = false, printed = false)
    }
}

/** Las 81 celdas de la lectura, en orden por filas. */
fun BoardReading.toCellReadings(): List<CellReading> = cells.map { cell ->
    val prediction = predictions[cell.index]
    CellReading(prediction.digit, prediction.confidence, inked = !cell.empty, printed = cell.printed)
}
