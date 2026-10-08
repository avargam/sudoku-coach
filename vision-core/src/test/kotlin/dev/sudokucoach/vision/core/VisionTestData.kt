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
        fun parse(text: String): PhotoLabel {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val header = lines.firstOrNull()?.takeIf { it.startsWith("#") }
            val metadata = header?.removePrefix("#")?.trim()?.split(Regex("\\s+"))
                ?.filter { "=" in it }?.associate { it.substringBefore("=") to it.substringAfter("=") }
                .orEmpty()
            val grid = lines.filterNot { it.startsWith("#") }
            require(grid.size == 18 && grid.all { it.length == 9 }) { "Se esperaban 18 líneas de 9 caracteres" }
            val digits = grid.take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }.toIntArray()
            val origins = grid.drop(9).joinToString("").map { Origin.of(it) }
            return PhotoLabel(metadata, digits, origins)
        }
    }
}

/** Una foto del conjunto con su etiqueta, si ya está etiquetada. */
data class TestPhoto(val id: String, val file: File, val label: PhotoLabel?) {
    fun read(): Mat {
        OpenCvTestSupport.ensureLoaded()
        return Imgcodecs.imread(file.absolutePath).also { require(!it.empty()) { "No se pudo leer ${file.name}" } }
    }
}

/** Acceso al conjunto de fotos de `testdata/vision/` (D-17). */
object VisionTestData {
    val root: File = File(System.getProperty("sudokucoach.testdata") ?: "testdata/vision")

    fun photos(): List<TestPhoto> =
        File(root, "photos").listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }.map { file ->
            val id = file.nameWithoutExtension
            val labelFile = File(root, "labels/$id.txt")
            TestPhoto(id, file, labelFile.takeIf { it.exists() }?.let { PhotoLabel.parse(it.readText()) })
        }
}
