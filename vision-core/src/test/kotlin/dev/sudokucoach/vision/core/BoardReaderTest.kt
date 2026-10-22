package dev.sudokucoach.vision.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.Mat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Flujo completo con un reconocedor falso: comprueba el encadenado, no el OCR (tarea 2.7). */
class BoardReaderTest {

    /** Reconocedor que siempre responde lo mismo y cuenta cuántas celdas recibe. */
    private class FixedRecognizer(private val digit: Int) : DigitRecognizer {
        var calls = 0
        override fun recognize(cell: Mat): Prediction {
            calls++
            return Prediction(digit, 0.5f)
        }
    }

    @Test
    fun `solo llama al reconocedor en las celdas con digito`() {
        val photo = VisionTestData.photos().firstOrNull { it.label != null }
        assumeTrue(photo != null, "No hay fotos etiquetadas")
        val recognizer = FixedRecognizer(7)
        val reading = assertNotNull(BoardReader(BoardRecognizer.cellByCell(recognizer)).read(photo!!.read()))

        val nonEmpty = reading.cells.count { !it.empty }
        assertEquals(nonEmpty, recognizer.calls)
        assertEquals(81, reading.predictions.size)
        reading.cells.forEach { cell -> assertEquals(if (cell.empty) 0 else 7, reading.digits[cell.index]) }
    }
}
