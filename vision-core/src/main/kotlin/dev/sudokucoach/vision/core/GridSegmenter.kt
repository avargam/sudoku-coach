package dev.sudokucoach.vision.core

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Una de las 81 celdas del tablero rectificado.
 *
 * @property index posición 0..80 por filas, como en el `solver`.
 * @property empty `true` si no hay ningún trazo con forma de dígito.
 * @property bounds caja del dígito en coordenadas del tablero rectificado (`null` si está vacía).
 * @property digit recorte cuadrado del dígito en gris, centrado y con margen, listo para un
 *   reconocedor (`null` si está vacía). Cada consumidor lo reescala a lo que necesite.
 * @property inkContrast oscuridad de la tinta respecto al papel de la celda, entre 0 y 1. El impreso es
 *   casi negro y el lápiz gris claro, así que sirve para estimar el origen (D-12, D-17).
 */
class Cell(val index: Int, val empty: Boolean, val bounds: Rect?, val digit: Mat?, val inkContrast: Double) {
    /** Si el dígito parece impreso y no escrito a lápiz; `false` en las celdas vacías. */
    val printed: Boolean get() = !empty && inkContrast >= PRINTED_CONTRAST

    companion object {
        /**
         * Contraste a partir del cual la tinta se considera impresa. El contraste es bimodal (lápiz
         * 10–30 %, impreso 50–65 %); con este umbral el origen acierta en el 99,9 % de los dígitos
         * de las fotos de prueba (tarea 2.4).
         */
        const val PRINTED_CONTRAST = 0.4
    }
}

/**
 * Divide el tablero rectificado en 81 celdas y decide cuáles están vacías (tarea 2.4).
 *
 * En lugar de contar la tinta de cada celda, une sus componentes conexas y mira la altura y la tinta
 * del conjunto. Unirlas importa con el lápiz: un 7 tenue se binariza como la barra horizontal más
 * varios trozos sueltos de la diagonal, ninguno con altura de dígito por sí solo. Se descartan los
 * trozos de línea de la rejilla que asoman por los bordes (tocan el borde y son finos) y las manchas
 * pegadas al borde; una mota aislada no llega a la altura ni a la tinta mínimas.
 */
class GridSegmenter(
    /**
     * Altura mínima del dígito (unión de manchas) respecto al lado de la celda. Con 0,35 se perdían los
     * lápices más tenues, que se binarizan a trozos: 4 en las fotos del conjunto y 6 en los originales
     * de la cámara reducidos en el móvil (tarea 3.3). Con 0,26 no se pierde ninguno y no aparece tinta
     * falsa hasta 0,24.
     */
    private val minDigitHeight: Double = 0.26,
    /** Tinta mínima del dígito, en píxeles del recorte de la celda. */
    private val minDigitInk: Int = 35,
    /** Las manchas deben tener su centro dentro de esta franja (fracción del recorte por cada lado). */
    private val edgeBand: Double = 0.08,
) {

    fun segment(rectified: Mat): List<Cell> {
        require(rectified.channels() == 1) { "Se esperaba el tablero rectificado en gris" }
        val binary = binarize(rectified)
        try {
            val cellSize = rectified.cols() / 9
            return (0 until 81).map { segmentCell(rectified, binary, it, cellSize) }
        } finally {
            binary.release()
        }
    }

    private fun segmentCell(gray: Mat, binary: Mat, index: Int, cellSize: Int): Cell {
        val margin = (cellSize * CELL_MARGIN).toInt()
        val inner = Rect((index % 9) * cellSize + margin, (index / 9) * cellSize + margin, cellSize - 2 * margin, cellSize - 2 * margin)
        val cellBinary = binary.submat(inner)
        val digitBox = digitBox(cellBinary, cellSize)
        if (digitBox == null) return Cell(index, empty = true, bounds = null, digit = null, inkContrast = 0.0)

        val bounds = Rect(inner.x + digitBox.x, inner.y + digitBox.y, digitBox.width, digitBox.height)
        return Cell(index, empty = false, bounds = bounds, digit = squareCrop(gray, bounds, cellSize), inkContrast = inkContrast(gray, binary, inner))
    }

    /** Caja que engloba las manchas del dígito, o `null` si la celda está vacía. */
    private fun digitBox(cellBinary: Mat, cellSize: Int): Rect? {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(cellBinary, labels, stats, centroids, 8, CvType.CV_32S)
        val w = cellBinary.cols()
        val h = cellBinary.rows()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        var ink = 0
        for (label in 1 until count) { // 0 es el fondo
            val area = stats.get(label, Imgproc.CC_STAT_AREA)[0].toInt()
            val x = stats.get(label, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = stats.get(label, Imgproc.CC_STAT_TOP)[0].toInt()
            val cw = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val ch = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val cx = centroids.get(label, 0)[0]
            val cy = centroids.get(label, 1)[0]
            // Restos de la rejilla: un trozo vertical fino pegado al borde izquierdo o derecho, uno horizontal
            // fino pegado al de arriba o abajo, o cualquier trazo fino casi tan largo como la celda. Un 1 alto
            // puede tocar el borde inferior y no es un resto (foto 003).
            val thinPx = w * LINE_REMNANT_WIDTH
            val verticalRemnant = cw <= thinPx && (x == 0 || x + cw == w)
            val horizontalRemnant = ch <= thinPx && (y == 0 || y + ch == h)
            val longStroke = min(cw, ch) <= thinPx && max(cw, ch) >= w * LINE_MIN_LENGTH
            val gridLineRemnant = verticalRemnant || horizontalRemnant || longStroke
            val nearEdge = cx !in w * edgeBand..w * (1 - edgeBand) || cy !in h * edgeBand..h * (1 - edgeBand)
            if (area < MIN_COMPONENT_AREA || gridLineRemnant || nearEdge) continue
            ink += area
            left = min(left, x)
            top = min(top, y)
            right = max(right, x + cw)
            bottom = max(bottom, y + ch)
        }
        listOf(labels, stats, centroids).forEach { it.release() }
        if (right < 0 || bottom - top < minDigitHeight * cellSize || ink < minDigitInk) return null
        return Rect(left, top, right - left, bottom - top)
    }

    /** Recorte cuadrado en gris alrededor del dígito, con un margen del 25 % del lado mayor. */
    private fun squareCrop(gray: Mat, bounds: Rect, cellSize: Int): Mat {
        val side = (max(bounds.width, bounds.height) * 1.5).toInt().coerceAtMost(cellSize)
        val cx = bounds.x + bounds.width / 2
        val cy = bounds.y + bounds.height / 2
        val x = (cx - side / 2).coerceIn(0, gray.cols() - side)
        val y = (cy - side / 2).coerceIn(0, gray.rows() - side)
        return gray.submat(Rect(x, y, side, side)).clone()
    }

    /** (papel − tinta) / papel, con la mediana aproximada del papel y la media de los píxeles de tinta. */
    private fun inkContrast(gray: Mat, binary: Mat, inner: Rect): Double {
        val cellGray = gray.submat(inner)
        val ink = binary.submat(inner)
        val paperMask = Mat()
        Core.bitwise_not(ink, paperMask)
        val inkMean = Core.mean(cellGray, ink).`val`[0]
        val paperMean = Core.mean(cellGray, paperMask).`val`[0]
        paperMask.release()
        return if (paperMean <= 0.0) 0.0 else ((paperMean - inkMean) / paperMean).coerceIn(0.0, 1.0)
    }

    /** Tinta en blanco sobre negro, sin las líneas de la rejilla. */
    private fun binarize(gray: Mat): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        val binary = Mat()
        // Ventana de 15 px (≈ 1/3 de celda): resalta los trazos a lápiz sin llenar las celdas de ruido.
        Imgproc.adaptiveThreshold(blurred, binary, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 7.0)
        blurred.release()
        removeGridLines(binary, gray.cols() / 9)
        return binary
    }

    /**
     * Borra los trazos rectos más largos que [LINE_KERNEL] celdas: en el tablero rectificado solo pueden
     * ser líneas de la rejilla, que lo atraviesan entero, porque un dígito nunca supera una celda (con una
     * celda justa se borraban los 1 altos, fotos 003 y 021). Hace falta porque, con el papel curvado,
     * una línea vertical y otra horizontal entran en el recorte como una sola mancha en forma de L que
     * los filtros por forma no reconocen (foto 009). La máscara se engrosa un poco para cubrir las
     * líneas ligeramente inclinadas.
     */
    private fun removeGridLines(binary: Mat, cellSize: Int) {
        val length = (cellSize * LINE_KERNEL).toInt()
        val horizontal = Mat()
        val vertical = Mat()
        Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(length.toDouble(), 1.0)))
        Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, length.toDouble())))
        val lines = Mat()
        Core.bitwise_or(horizontal, vertical, lines)
        Imgproc.dilate(lines, lines, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        Core.subtract(binary, lines, binary)
        listOf(horizontal, vertical, lines).forEach { it.release() }
    }

    companion object {
        /** Margen recortado por cada lado de la celda (≈ 10 %) para quitar las líneas de la rejilla. */
        const val CELL_MARGIN = 0.10
        /** Longitud mínima (en celdas) de un trazo recto para considerarlo línea de la rejilla. */
        private const val LINE_KERNEL = 1.5
        /** Las manchas más pequeñas son ruido del umbral. */
        private const val MIN_COMPONENT_AREA = 4
        /** Una mancha que toca el borde y es más fina que esto (fracción del recorte) es resto de la rejilla. */
        private const val LINE_REMNANT_WIDTH = 0.2
        /**
         * Una mancha fina al menos así de larga (fracción del recorte) es una línea: los restos de la rejilla
         * ocupan prácticamente todo el recorte, y un 1 muy alto puede llegar al 85 %.
         */
        private const val LINE_MIN_LENGTH = 0.95
    }
}
