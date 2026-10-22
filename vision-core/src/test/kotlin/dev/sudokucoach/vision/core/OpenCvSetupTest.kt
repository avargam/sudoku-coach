package dev.sudokucoach.vision.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Comprueba que OpenCV 4.14 funciona en la JVM (tarea 2.2) y que el conjunto de fotos se puede leer. */
class OpenCvSetupTest {

    init {
        OpenCvTestSupport.ensureLoaded()
    }

    @Test
    fun `la version de OpenCV es la misma que la del AAR de Android`() {
        assertTrue(Core.VERSION.startsWith("4.14"), "OpenCV ${Core.VERSION}")
    }

    @Test
    fun `procesa y codifica una imagen sintetica`() {
        // Rejilla dibujada: comprueba imgproc e imgcodecs, las dos piezas que usará la detección.
        val image = Mat(450, 450, CvType.CV_8UC1, Scalar(255.0))
        for (i in 0..9) {
            val p = i * 50.0
            Imgproc.line(image, Point(p, 0.0), Point(p, 449.0), Scalar(0.0), if (i % 3 == 0) 3 else 1)
            Imgproc.line(image, Point(0.0, p), Point(449.0, p), Scalar(0.0), if (i % 3 == 0) 3 else 1)
        }
        val encoded = MatOfByte()
        assertTrue(Imgcodecs.imencode(".png", image, encoded))
        val decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_GRAYSCALE)
        assertEquals(image.size(), decoded.size())
        assertEquals(0, Core.countNonZero(Mat().also { Core.absdiff(image, decoded, it) }))
    }

    @Test
    fun `lee las fotos del conjunto si ya existen`() {
        val photos = VisionTestData.photos()
        assumeTrue(photos.isNotEmpty(), "Todavía no hay fotos en ${VisionTestData.root}/photos (tarea 2.1)")
        for (photo in photos) {
            val image = photo.read()
            assertTrue(image.cols() > 0 && image.rows() > 0, photo.id)
        }
    }

    @Test
    fun `interpreta el formato de etiqueta`() {
        val text = """
            # luz=natural angulo=moderado papel=periodico
            2..3..8..
            .7...9.6.
            6....7.31
            .2.1...9.
            7........
            ..6...1.3
            ....1...5
            ...5.4..2
            .9.8...1.
            P..P..P..
            .M...P.P.
            P....P.PP
            .P.P...P.
            P........
            ..P...P.P
            ....P...P
            ...P.P..P
            .P.P...P.
        """.trimIndent()
        val label = PhotoLabel.parse(text)
        assertEquals("natural", label.metadata["luz"])
        assertEquals(2, label.digits[0])
        assertEquals(Origin.HANDWRITTEN, label.origins[10])
        assertEquals(Origin.EMPTY, label.origins[1])
    }
}
