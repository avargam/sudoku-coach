package dev.sudokucoach.vision.core

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Segmentación sobre tableros rectificados sintéticos (tarea 2.4). */
class GridSegmenterTest {

    init {
        OpenCvTestSupport.ensureLoaded()
    }

    private val segmenter = GridSegmenter()
    private val cell = 50.0

    /** Tablero rectificado de 450×450 con la rejilla y lo que dibuje [draw]. */
    private fun board(lineShift: Int = 0, draw: (Mat) -> Unit = {}): Mat {
        val board = Mat(450, 450, CvType.CV_8UC1, Scalar(225.0))
        for (i in 0..9) {
            val p = (i * cell).coerceAtMost(449.0) + if (i in 1..8) lineShift else 0
            val thickness = if (i % 3 == 0) 4 else 2
            Imgproc.line(board, Point(p, 0.0), Point(p, 449.0), Scalar(40.0), thickness)
            Imgproc.line(board, Point(0.0, p), Point(449.0, p), Scalar(40.0), thickness)
        }
        draw(board)
        return board
    }

    private fun digit(board: Mat, index: Int, text: String, ink: Double, thickness: Int, scale: Double = 1.1) {
        val origin = Point((index % 9) * cell + 15, (index / 9) * cell + 38)
        Imgproc.putText(board, text, origin, Imgproc.FONT_HERSHEY_SIMPLEX, scale, Scalar(ink), thickness)
    }

    @Test
    fun `distingue celdas vacias y con digito`() {
        val printed = setOf(0, 13, 40, 80)
        val pencil = setOf(5, 27, 66)
        val cells = segmenter.segment(board { b ->
            printed.forEach { digit(b, it, "8", ink = 30.0, thickness = 3) }
            pencil.forEach { digit(b, it, "4", ink = 150.0, thickness = 1) }
        })
        assertEquals(81, cells.size)
        assertEquals(printed + pencil, cells.filter { !it.empty }.map { it.index }.toSet())
    }

    @Test
    fun `el contraste separa impreso de lapiz`() {
        val cells = segmenter.segment(board { b ->
            digit(b, 10, "6", ink = 30.0, thickness = 3)
            digit(b, 20, "6", ink = 160.0, thickness = 1)
        })
        assertTrue(cells[10].printed && !cells[20].printed, "impreso %.2f, lápiz %.2f".format(cells[10].inkContrast, cells[20].inkContrast))
        assertFalse(cells[0].printed) // vacía
    }

    @Test
    fun `ignora motas y restos de lineas desplazadas por la curvatura`() {
        // Líneas desplazadas 4 px hacia dentro de las celdas y una mota en una celda vacía.
        val cells = segmenter.segment(board(lineShift = 4) { b ->
            Imgproc.circle(b, Point(4 * cell + 25, 4 * cell + 25), 2, Scalar(60.0), -1)
        })
        assertTrue(cells.all { it.empty }, "Celdas no vacías: ${cells.filter { !it.empty }.map { it.index }}")
    }

    @Test
    fun `un 1 alto que toca el borde del recorte no se toma por una linea`() {
        val cells = segmenter.segment(board { b ->
            val x = 3 * cell + 25
            Imgproc.line(b, Point(x, 1 * cell + 12), Point(x, 2 * cell - 3), Scalar(30.0), 4) // casi toda la altura de la celda
        })
        assertTrue(!cells[12].empty)
    }

    @Test
    fun `devuelve el recorte del digito y su caja`() {
        val cells = segmenter.segment(board { b -> digit(b, 40, "5", ink = 30.0, thickness = 3) })
        val c = cells[40]
        val bounds = assertNotNull(c.bounds)
        val crop = assertNotNull(c.digit)
        assertEquals(crop.cols(), crop.rows())
        assertTrue(crop.cols() > bounds.width && crop.cols() > bounds.height)
        assertTrue(bounds.x >= 4 * cell && bounds.x + bounds.width <= 5 * cell)
        assertNull(cells[41].digit)
    }
}
