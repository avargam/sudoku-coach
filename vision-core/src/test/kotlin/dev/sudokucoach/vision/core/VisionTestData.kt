package dev.sudokucoach.vision.core

import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/** Origen del contenido de una celda en la etiqueta (D-17). */
enum class Origin(val code: Char) {
    PRINTED('P'), HANDWRITTEN('M'), EMPTY('.');

    companion object {
        fun of(code: Char): Origin = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Origen desconocido '$code' (se esperaba P, M o .)")
    }
}

/**
 * Etiqueta de una foto: metadatos, 81 dígitos (0 = vacía) y el origen de cada celda.
 * Formato del fichero (ver `testdata/vision/README.md`): una línea `# clave=valor ...`,
 * 9 líneas de dígitos y 9 líneas de origen.
 */
data class PhotoLabel(val metadata: Map<String, String>, val digits: IntArray, val origins: List<Origin>) {
    init {
        require(digits.size == 81 && origins.size == 81) { "La etiqueta debe tener 81 celdas" }
        for (i in 0 until 81) {
            require((digits[i] == 0) == (origins[i] == Origin.EMPTY)) { "Celda $i: dígito ${digits[i]} con origen ${origins[i]}" }
        }
    }

    companion object {
        /** Carácter de las plantillas para un dígito aún sin transcribir. */
        const val PENDING = '?'

        fun parse(text: String): PhotoLabel {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val header = lines.firstOrNull()?.takeIf { it.startsWith("#") }
            val metadata = header?.removePrefix("#")?.trim()?.split(Regex("\\s+"))
                ?.filter { "=" in it }?.associate { it.substringBefore("=") to it.substringAfter("=") }
                .orEmpty()
            val grid = lines.filterNot { it.startsWith("#") }
            require(grid.size == 18 && grid.all { it.length == 9 }) { "Se esperaban 18 líneas de 9 caracteres" }
            require(grid.none { PENDING in it }) { "La etiqueta tiene celdas pendientes ('$PENDING')" }
            val digits = grid.take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }.toIntArray()
            val origins = grid.drop(9).joinToString("").map { Origin.of(it) }
            return PhotoLabel(metadata, digits, origins)
        }
    }
}

/**
 * Una foto del conjunto. [label] es `null` si no hay etiqueta o si está pendiente (plantilla con `?`
 * sin completar); [labelFile] es donde está o estará.
 */
data class TestPhoto(val id: String, val file: File, val labelFile: File, val label: PhotoLabel?) {
    val pending: Boolean get() = labelFile.exists() && label == null

    fun read(): Mat {
        OpenCvTestSupport.ensureLoaded()
        return Imgcodecs.imread(file.absolutePath).also { require(!it.empty()) { "No se pudo leer ${file.name}" } }
    }
}

/**
 * Una secuencia de fotogramas grabada en vivo (D-22, tarea 4.4): carpeta `testdata/vision/sequences/<id>`
 * con `NNNNNN.jpg` (ms desde el primero) y, una vez etiquetada, `label.txt` con el formato de las fotos.
 */
data class TestSequence(val id: String, val dir: File, val frames: List<File>, val label: PhotoLabel?) {
    val labelFile: File get() = File(dir, LABEL)

    companion object {
        const val LABEL = "label.txt"
    }
}

/** Acceso al conjunto de fotos de `testdata/vision/` (D-17) y a las secuencias grabadas (D-22). */
object VisionTestData {
    val root: File = File(System.getProperty("sudokucoach.testdata") ?: "testdata/vision")

    /** Secuencias copiadas del móvil con `tools/vision/pull_sequences.sh`; no se versionan. */
    fun sequences(): List<TestSequence> =
        File(root, "sequences").listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.map { dir ->
            val frames = dir.listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }
            val text = File(dir, TestSequence.LABEL).takeIf { it.exists() }?.readText()
            val label = text?.takeIf { lines -> lines.lines().none { !it.startsWith("#") && PhotoLabel.PENDING in it } }
                ?.let { PhotoLabel.parse(it) }
            TestSequence(dir.name, dir, frames, label)
        }

    fun photos(): List<TestPhoto> =
        File(root, "photos").listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }.map { file ->
            val id = file.nameWithoutExtension
            val labelFile = File(root, "labels/$id.txt")
            val text = labelFile.takeIf { it.exists() }?.readText()
            val label = text?.takeIf { lines -> lines.lines().none { !it.startsWith("#") && PhotoLabel.PENDING in it } }
                ?.let { PhotoLabel.parse(it) }
            TestPhoto(id, file, labelFile, label)
        }
}
