package dev.sudokucoach.vision.core

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * Etiquetado asistido (tarea 2.5, D-17). Para cada foto sin etiqueta:
 * - escribe `testdata/vision/labels/NNN.txt` con las celdas vacías detectadas (`.`), `?` donde hay
 *   un dígito por transcribir y el origen propuesto por el contraste de la tinta (`P`/`M`);
 * - guarda en `vision-core/build/labeling/NNN.png` el tablero rectificado con filas y columnas
 *   numeradas y las cajas de los dígitos (verde = impreso, azul = manuscrito propuesto).
 *
 * No sobrescribe etiquetas existentes salvo con `--force`. Uso:
 *   ./gradlew :vision-core:labelHelper [--args="003 004 --force"]
 */
fun main(args: Array<String>) {
    OpenCvTestSupport.ensureLoaded()
    val force = "--force" in args
    val only = args.filter { !it.startsWith("--") }.toSet()
    val review = File("build/labeling").apply { mkdirs() }
    File(VisionTestData.root, "labels").mkdirs()

    for (photo in VisionTestData.photos().filter { only.isEmpty() || it.id in only }) {
        val scan = BoardDetector().detect(photo.read())
        if (scan == null) {
            println("${photo.id}: tablero no detectado, se etiqueta a mano")
            continue
        }
        val cells = GridSegmenter().segment(scan.rectified)
        Imgcodecs.imwrite(File(review, "${photo.id}.png").path, reviewImage(scan.rectified, cells))

        if (photo.labelFile.exists() && !force) {
            println("${photo.id}: ya tiene etiqueta (usa --force para regenerarla); imagen de revisión actualizada")
            continue
        }
        photo.labelFile.writeText(template(cells))
        println("${photo.id}: plantilla con ${cells.count { !it.empty }} dígitos por transcribir")
    }
    println("Imágenes de revisión en ${review.absolutePath}")
}

/** Contraste a partir del cual se propone que la tinta es impresa (ver el histograma en 06-plan-fase-2.md). */
private const val PRINTED_CONTRAST = 0.4

private fun template(cells: List<Cell>): String = buildString {
    appendLine("# luz=? angulo=? papel=libro notas=")
    for (row in 0 until 9) appendLine(cells.subList(row * 9, row * 9 + 9).joinToString("") { if (it.empty) "." else "?" })
    for (row in 0 until 9) appendLine(cells.subList(row * 9, row * 9 + 9).joinToString("") { origin(it).toString() })
}

private fun origin(cell: Cell): Char = when {
    cell.empty -> Origin.EMPTY.code
    cell.inkContrast >= PRINTED_CONTRAST -> Origin.PRINTED.code
    else -> Origin.HANDWRITTEN.code
}

/** Tablero ampliado ×2 con márgenes para numerar filas y columnas, y las cajas de los dígitos. */
private fun reviewImage(rectified: Mat, cells: List<Cell>): Mat {
    val scale = 2.0
    val pad = 40
    val big = Mat()
    Imgproc.resize(rectified, big, Size(rectified.cols() * scale, rectified.rows() * scale))
    val color = Mat(big.rows() + pad, big.cols() + pad, org.opencv.core.CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))
    val colorBoard = Mat()
    Imgproc.cvtColor(big, colorBoard, Imgproc.COLOR_GRAY2BGR)
    colorBoard.copyTo(color.submat(pad, color.rows(), pad, color.cols()))
    val step = big.cols() / 9.0
    for (i in 0 until 9) {
        Imgproc.putText(color, "${i + 1}", Point(pad + i * step + step / 2 - 8, 28.0), 0, 0.9, Scalar(0.0, 0.0, 200.0), 2)
        Imgproc.putText(color, "${i + 1}", Point(10.0, pad + i * step + step / 2 + 10), 0, 0.9, Scalar(0.0, 0.0, 200.0), 2)
    }
    for (cell in cells) {
        val b = cell.bounds ?: continue
        val box = if (origin(cell) == Origin.PRINTED.code) Scalar(0.0, 150.0, 0.0) else Scalar(220.0, 90.0, 0.0)
        Imgproc.rectangle(
            color,
            Point(pad + b.x * scale, pad + b.y * scale),
            Point(pad + (b.x + b.width) * scale, pad + (b.y + b.height) * scale),
            box, 2,
        )
    }
    return color
}
