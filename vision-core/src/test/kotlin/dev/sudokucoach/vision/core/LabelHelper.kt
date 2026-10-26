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
 * Con `--sequences`, lo mismo para las secuencias grabadas (tarea 4.4), ver [labelSequences].
 *
 * No sobrescribe etiquetas existentes salvo con `--force`. Uso:
 *   ./gradlew :vision-core:labelHelper [--args="003 004 --force"]
 *   ./gradlew :vision-core:labelHelper --args="--sequences [S20261025-201411]"
 */
fun main(args: Array<String>) {
    OpenCvTestSupport.ensureLoaded()
    val force = "--force" in args
    val only = args.filter { !it.startsWith("--") }.toSet()
    val review = File("build/labeling").apply { mkdirs() }
    if ("--sequences" in args) {
        labelSequences(only, force, review)
        return
    }
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

private fun template(cells: List<Cell>, header: String = "# luz=? angulo=? papel=libro notas=", known: IntArray? = null): String = buildString {
    appendLine(header)
    for (row in 0 until 9) {
        appendLine(cells.subList(row * 9, row * 9 + 9).joinToString("") { cell ->
            when {
                cell.empty -> "."
                known != null && known[cell.index] != 0 -> known[cell.index].toString()
                else -> "?"
            }
        })
    }
    for (row in 0 until 9) appendLine(cells.subList(row * 9, row * 9 + 9).joinToString("") { origin(it).toString() })
}

/**
 * Plantillas de las secuencias (tarea 4.4). La etiqueta se hace sobre un fotograma de la propia
 * secuencia, no se copia de la foto, porque la página puede haber cambiado desde entonces: el
 * primer fotograma con tablero a partir del 70 % de la secuencia (ya quieto, según el guion de
 * grabación). Si las celdas con tinta y las impresas coinciden en un 90 % con las de la etiqueta de
 * una foto, se rellenan con ella las que tienen tinta en los dos; el resto queda con `?`. Los
 * impresos son las pistas del sudoku y lo identifican; la tinta sola no, porque todos los
 * tableros llenos tienen la misma. La cabecera
 * anota el fotograma y la foto usada, para revisarlos.
 */
private fun labelSequences(only: Set<String>, force: Boolean, review: File) {
    val photos = VisionTestData.photos().mapNotNull { p -> p.label?.let { p.id to it } }
    for (sequence in VisionTestData.sequences().filter { only.isEmpty() || it.id in only }) {
        val start = (sequence.frames.size * 0.7).toInt()
        val (frame, scan) = sequence.frames.drop(start).firstNotNullOfOrNull { file ->
            BoardDetector().detect(Imgcodecs.imread(file.path))?.let { file to it }
        } ?: run {
            println("${sequence.id}: ningún fotograma con tablero desde el 70 %")
            continue
        }
        val cells = GridSegmenter().segment(scan.rectified)
        Imgcodecs.imwrite(File(review, "${sequence.id}.png").path, reviewImage(scan.rectified, cells))
        if (sequence.labelFile.exists() && !force) {
            println("${sequence.id}: ya tiene etiqueta (usa --force para regenerarla); imagen de revisión actualizada")
            continue
        }
        val inked = cells.filter { !it.empty }.map { it.index }.toSet()
        val printed = cells.filter { it.printed }.map { it.index }.toSet()
        val match = photos.map { (id, label) ->
            val digits = (0 until 81).filter { label.digits[it] != 0 }.toSet()
            val givens = (0 until 81).filter { label.origins[it] == Origin.PRINTED }.toSet()
            Triple(id, label, minOf(jaccard(inked, digits), jaccard(printed, givens)))
        }.maxByOrNull { it.third }?.takeIf { it.third >= 0.9 }
        val header = "# frame=${frame.nameWithoutExtension} foto=${match?.first ?: "-"} notas="
        sequence.labelFile.writeText(template(cells, header, match?.second?.digits))
        val pending = cells.count { !it.empty && (match == null || match.second.digits[it.index] == 0) }
        println("${sequence.id}: fotograma ${frame.name}, foto ${match?.first ?: "ninguna"}, $pending dígitos por transcribir")
    }
    println("Imágenes de revisión en ${review.absolutePath}")
}

private fun origin(cell: Cell): Char = when {
    cell.empty -> Origin.EMPTY.code
    cell.printed -> Origin.PRINTED.code
    else -> Origin.HANDWRITTEN.code
}

private fun jaccard(a: Set<Int>, b: Set<Int>) = if (a.isEmpty() && b.isEmpty()) 1.0 else (a intersect b).size.toDouble() / (a union b).size

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
