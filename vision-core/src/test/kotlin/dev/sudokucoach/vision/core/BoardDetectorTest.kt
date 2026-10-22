package dev.sudokucoach.vision.core

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Detección sobre imágenes sintéticas con esquinas conocidas (tarea 2.3). Las fotos reales se
 * miden en la tarea 2.6.
 */
class BoardDetectorTest {

    init {
        OpenCvTestSupport.ensureLoaded()
    }

    private val detector = BoardDetector()

    /** Rejilla de sudoku de frente: fondo blanco, líneas finas y gruesas y algunos dígitos. */
    private fun frontalGrid(size: Int = 450): Mat {
        val grid = Mat(size, size, CvType.CV_8UC1, Scalar(250.0))
        val cell = size / 9.0
        for (i in 0..9) {
            val p = (i * cell).coerceAtMost(size - 1.0)
            val thickness = if (i % 3 == 0) 5 else 2
            Imgproc.line(grid, Point(p, 0.0), Point(p, size - 1.0), Scalar(20.0), thickness)
            Imgproc.line(grid, Point(0.0, p), Point(size - 1.0, p), Scalar(20.0), thickness)
        }
        for (k in 0 until 81 step 4) {
            val origin = Point((k % 9) * cell + cell * 0.3, (k / 9) * cell + cell * 0.75)
            Imgproc.putText(grid, "${k % 9 + 1}", origin, Imgproc.FONT_HERSHEY_SIMPLEX, 1.1, Scalar(30.0), 2)
        }
        return grid
    }

    /** "Foto" de 1200×1600 con la rejilla proyectada sobre [corners] y un fondo de papel gris claro. */
    private fun photo(corners: List<Point>, decorate: (Mat) -> Unit = {}): Mat {
        val canvas = Mat(1600, 1200, CvType.CV_8UC1, Scalar(205.0))
        decorate(canvas)
        val grid = frontalGrid()
        val s = grid.cols() - 1.0
        val homography = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(Point(0.0, 0.0), Point(s, 0.0), Point(s, s), Point(0.0, s)),
            MatOfPoint2f(*corners.toTypedArray()),
        )
        val warped = Mat()
        Imgproc.warpPerspective(grid, warped, homography, canvas.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
        val mask = Mat()
        Imgproc.warpPerspective(Mat(grid.size(), CvType.CV_8UC1, Scalar(255.0)), mask, homography, canvas.size())
        warped.copyTo(canvas, mask)
        Imgproc.GaussianBlur(canvas, canvas, Size(3.0, 3.0), 0.0)
        val noise = Mat(canvas.size(), CvType.CV_8UC1)
        Core.randn(noise, 0.0, 4.0)
        Core.add(canvas, noise, canvas)
        return canvas
    }

    /** Esquinas de un cuadrado de lado [side] centrado en (cx, cy) y girado [degrees]. */
    private fun square(cx: Double, cy: Double, side: Double, degrees: Double): List<Point> {
        val r = Math.toRadians(degrees)
        val h = side / 2
        return listOf(-h to -h, h to -h, h to h, -h to h).map { (x, y) ->
            Point(cx + x * cos(r) - y * sin(r), cy + x * sin(r) + y * cos(r))
        }
    }

    private fun assertCornersNear(expected: List<Point>, actual: List<Point>, tolerance: Double = 8.0) {
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            val d = hypot(e.x - a.x, e.y - a.y)
            assertTrue(d <= tolerance, "Esquina $i a %.1f px (esperada %s, obtenida %s)".format(d, e, a))
        }
    }

    @Test
    fun `detecta un tablero de frente`() {
        val corners = square(600.0, 850.0, 900.0, 0.0)
        val scan = assertNotNull(detector.detect(photo(corners)))
        assertCornersNear(corners, scan.corners)
        assertEquals(BoardDetector.RECTIFIED_SIZE, scan.rectified.cols())
        assertTrue(scan.gridScore >= 0.9, "rejilla ${scan.gridScore}")
    }

    @Test
    fun `detecta un tablero girado`() {
        val corners = square(600.0, 800.0, 850.0, 12.0)
        assertCornersNear(BoardDetector.orderCorners(corners), assertNotNull(detector.detect(photo(corners))).corners)
    }

    @Test
    fun `detecta un tablero con perspectiva`() {
        // Trapecio: la parte superior más estrecha, como una foto tomada desde abajo.
        val corners = listOf(Point(300.0, 350.0), Point(930.0, 380.0), Point(1080.0, 1250.0), Point(140.0, 1220.0))
        assertCornersNear(corners, assertNotNull(detector.detect(photo(corners))).corners)
    }

    @Test
    fun `ignora un marco mayor que no es una rejilla y los sudokus pequenos de la pagina`() {
        val corners = square(600.0, 900.0, 700.0, 3.0)
        val image = photo(corners) { canvas ->
            // Marco grande y vacío alrededor del tablero (p. ej. el borde de un recuadro de la página).
            Imgproc.rectangle(canvas, Point(100.0, 380.0), Point(1100.0, 1420.0), Scalar(20.0), 6)
            // Recorte de otro sudoku en la esquina y una barra gris rellena, como en el libro de las fotos.
            Imgproc.rectangle(canvas, Point(820.0, 30.0), Point(1150.0, 330.0), Scalar(20.0), 4)
            for (i in 1..5) Imgproc.line(canvas, Point(820.0 + i * 55, 30.0), Point(820.0 + i * 55, 330.0), Scalar(20.0), 2)
            Imgproc.rectangle(canvas, Point(200.0, 40.0), Point(300.0, 320.0), Scalar(110.0), -1)
        }
        assertCornersNear(BoardDetector.orderCorners(corners), assertNotNull(detector.detect(image)).corners)
    }

    @Test
    fun `no detecta nada sin tablero`() {
        val blank = Mat(1600, 1200, CvType.CV_8UC1, Scalar(205.0))
        Imgproc.rectangle(blank, Point(150.0, 300.0), Point(1050.0, 1250.0), Scalar(20.0), 6) // cuadrado sin rejilla
        assertNull(detector.detect(blank))
    }

    @Test
    fun `la puntuacion de rejilla distingue una rejilla de un cuadrado vacio`() {
        assertTrue(detector.gridScore(frontalGrid()) >= 0.9)
        val emptySquare = Mat(450, 450, CvType.CV_8UC1, Scalar(250.0))
        Imgproc.rectangle(emptySquare, Point(0.0, 0.0), Point(449.0, 449.0), Scalar(20.0), 5)
        assertTrue(detector.gridScore(emptySquare) < 0.5)
    }

    @Test
    fun `ordena las esquinas en sentido horario desde la superior izquierda`() {
        val shuffled = listOf(Point(10.0, 900.0), Point(900.0, 20.0), Point(15.0, 10.0), Point(890.0, 880.0))
        val ordered = BoardDetector.orderCorners(shuffled)
        assertEquals(listOf(Point(15.0, 10.0), Point(900.0, 20.0), Point(890.0, 880.0), Point(10.0, 900.0)), ordered)
    }
}
