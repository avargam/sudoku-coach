package dev.sudokucoach.vision

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.sudokucoach.vision.core.Cell
import dev.sudokucoach.vision.core.Prediction
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

/**
 * Estrategia "cuadrícula entera" (D-08): una sola pasada de ML Kit sobre el tablero rectificado,
 * asignando cada dígito leído a la celda que contiene el centro de su caja. Se compara con
 * [MlKitDigitRecognizer] en la tarea 2.8. Síncrono: no llamar desde el hilo principal.
 *
 * Antes de pasarlo a ML Kit se deja en blanco todo lo que no sea la caja de un dígito según el
 * segmentador: con las líneas de la rejilla, ML Kit no detectaba la mitad de los dígitos sueltos
 * (en el tablero sintético del test leía 7 de 11).
 */
class MlKitGridReader(
    private val client: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val scale: Double = 2.0,
) : Closeable {

    /** Una predicción por celda; las vacías según el segmentador quedan como 0 con confianza 1. */
    fun read(rectified: Mat, cells: List<Cell>): List<Prediction> {
        val digitsOnly = digitsOnly(rectified, cells)
        val stretched = Mat()
        Core.normalize(digitsOnly, stretched, 0.0, 255.0, Core.NORM_MINMAX)
        val enlarged = Mat()
        Imgproc.resize(stretched, enlarged, Size(), scale, scale, Imgproc.INTER_CUBIC)
        val bitmap = enlarged.toBitmap()
        listOf(digitsOnly, stretched, enlarged).forEach { it.release() }

        val text = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)))
        val cellSize = rectified.cols() / 9.0
        val best = HashMap<Int, Prediction>()
        for (symbol in text.digitSymbols()) {
            val col = (symbol.box.exactCenterX() / scale / cellSize).toInt()
            val row = (symbol.box.exactCenterY() / scale / cellSize).toInt()
            if (row !in 0..8 || col !in 0..8) continue
            val index = row * 9 + col
            val previous = best[index]
            if (previous == null || symbol.confidence > previous.confidence) best[index] = Prediction(symbol.digit, symbol.confidence)
        }
        return cells.map { cell ->
            when {
                cell.empty -> EMPTY
                else -> best[cell.index] ?: UNREADABLE
            }
        }
    }

    /** Copia del tablero en blanco salvo las cajas de los dígitos (con un pequeño margen). */
    private fun digitsOnly(rectified: Mat, cells: List<Cell>): Mat {
        val clean = Mat(rectified.size(), rectified.type(), Scalar(255.0))
        val all = Rect(0, 0, rectified.cols(), rectified.rows())
        for (cell in cells) {
            val b = cell.bounds ?: continue
            val box = intersect(Rect(b.x - BOX_PADDING, b.y - BOX_PADDING, b.width + 2 * BOX_PADDING, b.height + 2 * BOX_PADDING), all)
            rectified.submat(box).copyTo(clean.submat(box))
        }
        return clean
    }

    private fun intersect(a: Rect, b: Rect): Rect {
        val x = max(a.x, b.x)
        val y = max(a.y, b.y)
        return Rect(x, y, min(a.x + a.width, b.x + b.width) - x, min(a.y + a.height, b.y + b.height) - y)
    }

    override fun close() = client.close()

    private companion object {
        /** Margen alrededor de la caja del dígito; menor que el margen de celda, para no recuperar la rejilla. */
        const val BOX_PADDING = 2
        val EMPTY = Prediction(digit = 0, confidence = 1f)
        val UNREADABLE = Prediction(digit = 0, confidence = 0f)
    }
}
