package dev.sudokucoach.vision.core

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Tablero localizado en una foto.
 *
 * @property corners esquinas en la foto original, en orden: superior izquierda, superior derecha,
 *   inferior derecha, inferior izquierda.
 * @property rectified tablero visto de frente, de [BoardDetector.RECTIFIED_SIZE] px de lado, en gris.
 * @property gridScore entre 0 y 1: cuánto se parece la imagen rectificada a una rejilla 9×9.
 */
class BoardScan(val corners: List<Point>, val rectified: Mat, val gridScore: Double)

/**
 * Localiza el tablero de sudoku en una foto y lo rectifica (D-07, tarea 2.3).
 *
 * Busca el contorno más grande que sea un cuadrilátero convexo y de forma casi cuadrada, lo lleva
 * a un cuadrado con una homografía y comprueba que en él aparecen las líneas de una rejilla 9×9.
 * Los contornos que no la superan (otros cuadros de la página, recortes de otros sudokus más
 * pequeños, etc.) se descartan y se prueba el siguiente.
 */
class BoardDetector(
    /** Área mínima del tablero respecto a la imagen; descarta los sudokus pequeños de la página. */
    private val minAreaFraction: Double = 0.10,
    /** Relación máxima entre el lado más largo y el más corto del cuadrilátero. */
    private val maxSideRatio: Double = 1.6,
    /** Puntuación mínima de rejilla para aceptar un candidato. */
    private val minGridScore: Double = 0.5,
) {

    /** Devuelve el tablero encontrado, o `null` si ningún candidato pasa la validación. */
    fun detect(image: Mat): BoardScan? {
        val gray = toGray(image)
        try {
            for (quad in candidateQuads(gray)) {
                val rectified = rectify(gray, quad)
                val score = gridScore(rectified)
                if (score >= minGridScore) return BoardScan(quad, rectified, score)
                rectified.release()
            }
            return null
        } finally {
            gray.release()
        }
    }

    /** Cuadriláteros plausibles, de mayor a menor área. */
    internal fun candidateQuads(gray: Mat): List<List<Point>> {
        val binary = binarize(gray)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()

        val minArea = minAreaFraction * gray.total()
        val quads = contours
            .map { it to Imgproc.contourArea(it) }
            .filter { (_, area) -> area >= minArea }
            .sortedByDescending { (_, area) -> area }
            .mapNotNull { (contour, _) -> approximateQuad(contour) }
            .filter { isPlausibleBoard(it) }
        contours.forEach { it.release() }
        return quads
    }

    /** Lleva el cuadrilátero a un cuadrado de [RECTIFIED_SIZE] px. */
    fun rectify(gray: Mat, corners: List<Point>): Mat {
        val s = RECTIFIED_SIZE - 1.0
        val source = MatOfPoint2f(*corners.toTypedArray())
        val target = MatOfPoint2f(Point(0.0, 0.0), Point(s, 0.0), Point(s, s), Point(0.0, s))
        val homography = Imgproc.getPerspectiveTransform(source, target)
        val rectified = Mat()
        Imgproc.warpPerspective(gray, rectified, homography, Size(RECTIFIED_SIZE.toDouble(), RECTIFIED_SIZE.toDouble()))
        listOf(source, target, homography).forEach { it.release() }
        return rectified
    }

    /**
     * Puntuación de rejilla: para cada una de las 10 líneas verticales y horizontales esperadas,
     * compara la tinta en una franja estrecha alrededor de su posición con la del centro de las
     * celdas vecinas. Devuelve la fracción de líneas claramente más oscuras que su entorno.
     */
    fun gridScore(rectified: Mat): Double {
        val binary = binarize(rectified)
        val columns = Mat()
        val rows = Mat()
        Core.reduce(binary, columns, 0, Core.REDUCE_AVG, CvType.CV_64F) // tinta media por columna
        Core.reduce(binary, rows, 1, Core.REDUCE_AVG, CvType.CV_64F) // tinta media por fila
        val profileX = DoubleArray(RECTIFIED_SIZE) { columns.get(0, it)[0] / 255.0 }
        val profileY = DoubleArray(RECTIFIED_SIZE) { rows.get(it, 0)[0] / 255.0 }
        listOf(binary, columns, rows).forEach { it.release() }

        val lines = (0..9).count { isLine(profileX, it) } + (0..9).count { isLine(profileY, it) }
        return lines / 20.0
    }

    /** ¿Hay una línea de rejilla cerca de la posición `index` (0..9)? Se tolera un pequeño desplazamiento. */
    private fun isLine(profile: DoubleArray, index: Int): Boolean {
        val cell = RECTIFIED_SIZE / 9.0
        val expected = (index * cell).toInt().coerceIn(0, RECTIFIED_SIZE - 1)
        val tolerance = (cell * 0.12).toInt()
        val peak = (max(0, expected - tolerance)..min(RECTIFIED_SIZE - 1, expected + tolerance)).maxOf { profile[it] }
        // Fondo: el centro de las celdas a cada lado de la línea (donde no debería haber líneas).
        val background = listOf(expected - cell / 2, expected + cell / 2)
            .map { it.toInt() }
            .filter { it in 0 until RECTIFIED_SIZE }
            .map { profile[it] }
            .average()
        return peak >= LINE_MIN_INK && peak >= background + LINE_MIN_CONTRAST
    }

    /**
     * Aproxima el contorno por un cuadrilátero. Se trabaja sobre su envolvente convexa y se prueba con
     * tolerancias crecientes: si el papel está curvado, el borde del tablero se dobla y con la tolerancia
     * más estricta salen 5–8 vértices (foto 030 del conjunto). Las validaciones posteriores descartan
     * los cuadriláteros que no sean un tablero.
     */
    private fun approximateQuad(contour: MatOfPoint): List<Point>? {
        val hullIndices = MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val all = contour.toArray()
        val hull = MatOfPoint2f(*hullIndices.toArray().map { all[it] }.toTypedArray())
        val perimeter = Imgproc.arcLength(hull, true)
        val approx = MatOfPoint2f()
        try {
            for (epsilon in APPROX_EPSILONS) {
                Imgproc.approxPolyDP(hull, approx, epsilon * perimeter, true)
                val points = approx.toArray()
                if (points.size == 4) return orderCorners(points.toList())
                if (points.size < 4) return null
            }
            return null
        } finally {
            listOf(hullIndices, hull, approx).forEach { it.release() }
        }
    }

    /** Lados de longitud parecida y ángulos no demasiado alejados de 90°. */
    private fun isPlausibleBoard(quad: List<Point>): Boolean {
        val sides = (0..3).map { distance(quad[it], quad[(it + 1) % 4]) }
        if (sides.max() / sides.min() > maxSideRatio) return false
        return (0..3).all { abs(angle(quad[(it + 3) % 4], quad[it], quad[(it + 1) % 4]) - 90.0) <= MAX_ANGLE_DEVIATION }
    }

    private fun toGray(image: Mat): Mat = Mat().also {
        when (image.channels()) {
            1 -> image.copyTo(it)
            4 -> Imgproc.cvtColor(image, it, Imgproc.COLOR_BGRA2GRAY)
            else -> Imgproc.cvtColor(image, it, Imgproc.COLOR_BGR2GRAY)
        }
    }

    /** Tinta en blanco sobre fondo negro, robusto a iluminación desigual. */
    private fun binarize(gray: Mat): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val binary = Mat()
        // Ventana proporcional al tamaño para que funcione igual en la foto y en el tablero rectificado.
        val block = (min(gray.cols(), gray.rows()) / 40) or 1
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, max(block, 11), 7.0,
        )
        blurred.release()
        return binary
    }

    companion object {
        /** Lado del tablero rectificado: 9 celdas de 50 px. */
        const val RECTIFIED_SIZE = 450

        /** Tolerancias de aproximación (fracción del perímetro), de más estricta a más permisiva. */
        private val APPROX_EPSILONS = listOf(0.02, 0.03, 0.04, 0.05)
        private const val MAX_ANGLE_DEVIATION = 30.0
        private const val LINE_MIN_INK = 0.25
        private const val LINE_MIN_CONTRAST = 0.15

        /** Ordena 4 puntos como superior izquierda, superior derecha, inferior derecha, inferior izquierda. */
        fun orderCorners(points: List<Point>): List<Point> {
            require(points.size == 4) { "Se esperaban 4 esquinas" }
            val topLeft = points.minBy { it.x + it.y }
            val bottomRight = points.maxBy { it.x + it.y }
            val topRight = points.minBy { it.y - it.x }
            val bottomLeft = points.maxBy { it.y - it.x }
            return listOf(topLeft, topRight, bottomRight, bottomLeft)
        }

        private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

        private fun angle(a: Point, vertex: Point, b: Point): Double {
            val v1x = a.x - vertex.x
            val v1y = a.y - vertex.y
            val v2x = b.x - vertex.x
            val v2y = b.y - vertex.y
            val cos = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y))
            return Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
        }
    }
}
