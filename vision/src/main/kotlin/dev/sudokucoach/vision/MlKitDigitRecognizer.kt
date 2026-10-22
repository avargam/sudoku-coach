package dev.sudokucoach.vision

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.sudokucoach.vision.core.DigitRecognizer
import dev.sudokucoach.vision.core.Prediction
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.Closeable

/**
 * Estrategia "celda a celda" (D-08): reconoce el recorte de un dígito con ML Kit.
 *
 * ML Kit funciona mal con imágenes diminutas y con trazos tenues, así que el recorte se estira
 * al rango completo de grises, se amplía a [glyphSize] px y se rodea de un margen blanco.
 * Es síncrono (espera la tarea de ML Kit): no debe llamarse desde el hilo principal.
 * Si no se lee ningún dígito devuelve 0 con confianza 0, que la app trata como "por confirmar".
 */
class MlKitDigitRecognizer(
    private val client: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val glyphSize: Int = 64,
    private val padding: Int = 32,
) : DigitRecognizer, Closeable {

    override fun recognize(cell: Mat): Prediction {
        val prepared = prepare(cell)
        val bitmap = prepared.toBitmap()
        prepared.release()
        val text = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)))
        val best = text.digitSymbols().maxByOrNull { it.confidence } ?: return Prediction.UNREADABLE
        return Prediction(best.digit, best.confidence)
    }

    private fun prepare(cell: Mat): Mat {
        val stretched = Mat()
        Core.normalize(cell, stretched, 0.0, 255.0, Core.NORM_MINMAX)
        val resized = Mat()
        Imgproc.resize(stretched, resized, Size(glyphSize.toDouble(), glyphSize.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val padded = Mat()
        Core.copyMakeBorder(resized, padded, padding, padding, padding, padding, Core.BORDER_CONSTANT, Scalar(255.0))
        stretched.release()
        resized.release()
        return padded
    }

    override fun close() = client.close()
}
