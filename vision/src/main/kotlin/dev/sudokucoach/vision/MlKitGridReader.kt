package dev.sudokucoach.vision

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.sudokucoach.vision.core.BoardRecognizer
import dev.sudokucoach.vision.core.Cell
import dev.sudokucoach.vision.core.Prediction
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import kotlin.math.max

/**
 * Estrategia "cuadrícula entera" (D-08): reconoce todos los dígitos del tablero con una sola
 * llamada a ML Kit. Síncrono: no llamar desde el hilo principal.
 *
 * ML Kit es un lector de líneas de texto, no de caracteres sueltos, así que no se le pasa el tablero
 * tal cual sino una "lámina" dibujada a partir de él: los dígitos en líneas, seguidos, todos a la
 * misma altura, con el contraste estirado (papel blanco, tinta negra) y sin la rejilla. Cada lectura
 * se asigna a la celda cuyo glifo contiene el centro de su caja.
 * Medido en el A57 sobre las 30 fotos (tarea 2.8): con el tablero rectificado leía el 23 % de los
 * impresos; con la lámina, prácticamente todos. La separación entre glifos importa: con huecos
 * pequeños ML Kit une dígitos y con huecos grandes deja de leer filas enteras; el óptimo está en
 * una separación igual a la altura del glifo.
 */
class MlKitGridReader(
    private val client: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    /** Altura de cada dígito en la lámina, en px. */
    private val glyphHeight: Int = 48,
    /** Separación horizontal entre dígitos consecutivos de una fila. */
    private val glyphGap: Int = 48,
    /** Separación vertical entre filas. */
    private val lineGap: Int = 40,
) : BoardRecognizer, Closeable {

    /** Una predicción por celda; las vacías según el segmentador quedan como [Prediction.EMPTY]. */
    override fun recognize(rectified: Mat, cells: List<Cell>): List<Prediction> {
        val digits = cells.filter { it.bounds != null }
        val lines = layout(digits)
        val sheet = Mat(lines.size * (glyphHeight + lineGap) + lineGap, 2 * glyphHeight + MAX_PER_LINE * (glyphHeight + glyphGap), CvType.CV_8UC1, Scalar(255.0))
        val spans = lines.map { HashMap<Int, IntRange>() } // por línea: celda → columnas de la lámina que le corresponden
        for ((line, lineCells) in lines.withIndex()) {
            var x = glyphHeight
            for (cell in lineCells) {
                val glyph = glyph(rectified, cell.bounds!!)
                glyph.copyTo(sheet.submat(Rect(x, lineGap + line * (glyphHeight + lineGap), glyph.cols(), glyphHeight)))
                spans[line][cell.index] = (x - glyphGap / 2) until (x + glyph.cols() + glyphGap / 2)
                x += glyph.cols() + glyphGap
                glyph.release()
            }
        }
        val bitmap = sheet.toBitmap()
        sheet.release()

        val text = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)))
        val best = HashMap<Int, Prediction>()
        for (symbol in text.digitSymbols()) {
            val line = ((symbol.box.exactCenterY() - lineGap / 2f) / (glyphHeight + lineGap)).toInt()
            val x = symbol.box.exactCenterX().toInt()
            val index = spans.getOrNull(line)?.entries?.firstOrNull { x in it.value }?.key ?: continue
            val previous = best[index]
            if (previous == null || symbol.confidence > previous.confidence) best[index] = Prediction(symbol.digit, symbol.confidence)
        }
        return cells.map { cell ->
            when {
                cell.empty -> Prediction.EMPTY
                else -> best[cell.index] ?: Prediction.UNREADABLE
            }
        }
    }

    /**
     * Reparte los dígitos, en orden, en el mínimo de líneas de como mucho [MAX_PER_LINE] y con el mismo
     * número en cada una. ML Kit no lee las líneas de un solo carácter, así que no se usa una línea por
     * fila del tablero (una fila puede tener un único dígito); repartidos así, un sudoku válido, con 17
     * dígitos o más, da líneas de al menos 8.
     */
    private fun layout(digits: List<Cell>): List<List<Cell>> {
        if (digits.isEmpty()) return emptyList()
        val lines = (digits.size + MAX_PER_LINE - 1) / MAX_PER_LINE
        return (0 until lines).map { line -> digits.subList(line * digits.size / lines, (line + 1) * digits.size / lines) }
    }

    /** Dígito con el contraste estirado y escalado a [glyphHeight] px de alto (y como mucho otro tanto de ancho). */
    private fun glyph(rectified: Mat, bounds: Rect): Mat {
        val stretched = stretchContrast(rectified.submat(bounds))
        val width = max(1, bounds.width * glyphHeight / bounds.height).coerceAtMost(glyphHeight)
        val glyph = Mat()
        Imgproc.resize(stretched, glyph, Size(width.toDouble(), glyphHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        stretched.release()
        return glyph
    }

    /**
     * Lleva el papel a blanco y la tinta más oscura a negro. Imprescindible para el lápiz: sin esto,
     * ML Kit apenas lee los manuscritos. El papel es la media de la parte clara según Otsu.
     */
    private fun stretchContrast(gray: Mat): Mat {
        val paperMask = Mat()
        Imgproc.threshold(gray, paperMask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val paper = Core.mean(gray, paperMask).`val`[0]
        paperMask.release()
        val ink = Core.minMaxLoc(gray).minVal
        val alpha = 255.0 / max(1.0, paper - ink)
        val stretched = Mat()
        gray.convertTo(stretched, CvType.CV_8U, alpha, -ink * alpha)
        return stretched
    }

    override fun close() = client.close()

    private companion object {
        /** Dígitos por línea de la lámina, como una fila del tablero. */
        const val MAX_PER_LINE = 9
    }
}
