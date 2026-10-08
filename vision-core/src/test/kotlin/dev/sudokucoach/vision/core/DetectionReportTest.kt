package dev.sudokucoach.vision.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.test.Test

/**
 * Informe de detección sobre las fotos reales: guarda en `build/vision-debug/` la foto con el
 * cuadrilátero detectado y el tablero rectificado, para revisarlos a ojo. Las métricas con umbral
 * llegan en la tarea 2.6.
 */
class DetectionReportTest {

    @Test
    fun `informe de deteccion sobre las fotos`() {
        val photos = VisionTestData.photos()
        assumeTrue(photos.isNotEmpty(), "No hay fotos (tarea 2.1)")
        val out = File("build/vision-debug").apply { mkdirs() }
        val detector = BoardDetector()
        var found = 0
        for (photo in photos) {
            val image = photo.read()
            val scan = detector.detect(image)
            if (scan != null) {
                found++
                Imgproc.polylines(image, listOf(MatOfPoint(*scan.corners.toTypedArray())), true, Scalar(0.0, 0.0, 255.0), 6)
                scan.corners.forEachIndexed { i, p -> Imgproc.putText(image, "$i", Point(p.x + 8, p.y + 30), 0, 1.2, Scalar(255.0, 0.0, 0.0), 3) }
                Imgcodecs.imwrite(File(out, "${photo.id}-rect.png").path, scan.rectified)
            }
            Imgcodecs.imwrite(File(out, "${photo.id}-quad.jpg").path, image)
            println("${photo.id}: ${if (scan == null) "NO DETECTADO" else "rejilla %.2f".format(scan.gridScore)}")
        }
        println("Detectados: $found/${photos.size}")
    }
}
