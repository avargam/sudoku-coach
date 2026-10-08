package dev.sudokucoach.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sudokucoach.vision.core.GridSegmenter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Los dos reconocedores de ML Kit sobre dígitos impresos sintéticos (tarea 2.7). */
@RunWith(AndroidJUnit4::class)
class MlKitRecognizersTest {

    @Test
    fun celdaACeldaReconoceLosNueveDigitos() {
        MlKitDigitRecognizer().use { recognizer ->
            val read = (1..9).map { d -> recognizer.recognize(SyntheticImages.digit(d).toGrayMat()).digit }
            assertEquals((1..9).toList(), read)
        }
    }

    @Test
    fun cuadriculaEnteraAsignaCadaDigitoASuCelda() {
        // Diagonal 1..9 y algunos dígitos más; el resto vacío.
        val digits = IntArray(81)
        for (i in 0 until 9) digits[i * 10] = i + 1
        digits[8] = 4
        digits[72] = 6
        val rectified = SyntheticImages.board(digits).toGrayMat()
        val cells = GridSegmenter().segment(rectified)
        assertEquals(digits.map { it != 0 }, cells.map { !it.empty }) // el segmentador encuentra las mismas celdas

        val read = MlKitGridReader().use { it.read(rectified, cells) }.map { it.digit }
        assertEquals(digits.toList(), read)
    }
}
