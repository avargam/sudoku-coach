package dev.sudokucoach.vision.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Métricas de visión en la JVM sobre las fotos etiquetadas (tarea 2.6): detección del tablero,
 * celdas vacías y origen propuesto. Escribe un informe en `build/reports/vision/metrics.md` y exige
 * los umbrales de la Fase 2. El reconocimiento de dígitos se mide en el móvil (tarea 2.8).
 */
class VisionMetricsTest {

    /** Resultado de una foto. `cells` es null si el tablero no se detectó. */
    private class PhotoResult(val photo: TestPhoto, val label: PhotoLabel, val cells: List<Cell>?, val millis: Long)

    private class Counter(var ok: Int = 0, var total: Int = 0) {
        fun add(correct: Boolean) {
            total++
            if (correct) ok++
        }
        val rate: Double get() = if (total == 0) 1.0 else ok.toDouble() / total
        override fun toString() = "$ok/$total (%.1f %%)".format(rate * 100)
    }

    @Test
    fun `metricas de vision sobre las fotos etiquetadas`() {
        val photos = VisionTestData.photos().filter { it.label != null }
        assumeTrue(photos.isNotEmpty(), "No hay fotos etiquetadas")
        val detector = BoardDetector()
        val segmenter = GridSegmenter()

        detector.detect(photos.first().read()) // calentamiento de la JVM para que el tiempo sea representativo
        val results = photos.map { photo ->
            val image = photo.read()
            val start = System.nanoTime()
            val cells = detector.detect(image)?.let { segmenter.segment(it.rectified) }
            PhotoResult(photo, photo.label!!, cells, (System.nanoTime() - start) / 1_000_000)
        }

        val detection = Counter()
        val emptiness = Counter()
        val emptinessByOrigin = Origin.entries.associateWith { Counter() }
        val origin = Counter()
        val byAngle = sortedMapOf<String, Pair<Counter, Counter>>() // ángulo → (detección, vacías)
        val failures = mutableListOf<String>()

        for (r in results) {
            val angle = r.label.metadata["angulo"] ?: "?"
            val (angleDetection, angleEmptiness) = byAngle.getOrPut(angle) { Counter() to Counter() }
            detection.add(r.cells != null)
            angleDetection.add(r.cells != null)
            val cells = r.cells ?: run { failures += "${r.photo.id}: tablero no detectado"; continue }
            for (cell in cells) {
                val labelOrigin = r.label.origins[cell.index]
                val correct = (labelOrigin == Origin.EMPTY) == cell.empty
                emptiness.add(correct)
                angleEmptiness.add(correct)
                emptinessByOrigin.getValue(labelOrigin).add(correct)
                if (!correct) failures += "${r.photo.id} F${cell.index / 9 + 1}C${cell.index % 9 + 1}: " +
                    if (cell.empty) "dígito ${if (labelOrigin == Origin.PRINTED) "impreso" else "manuscrito"} no detectado" else "celda vacía tomada por dígito"
                if (!cell.empty && labelOrigin != Origin.EMPTY) {
                    origin.add(proposedOrigin(cell) == labelOrigin)
                }
            }
        }

        val report = buildString {
            appendLine("# Métricas de visión (JVM)")
            appendLine()
            appendLine("Fotos etiquetadas: ${results.size}")
            appendLine()
            appendLine("| Métrica | Resultado |")
            appendLine("|---|---|")
            appendLine("| Tableros detectados | $detection |")
            appendLine("| Celdas vacía/no vacía correctas | $emptiness |")
            appendLine("| — con dígito impreso | ${emptinessByOrigin[Origin.PRINTED]} |")
            appendLine("| — con dígito manuscrito | ${emptinessByOrigin[Origin.HANDWRITTEN]} |")
            appendLine("| — vacías | ${emptinessByOrigin[Origin.EMPTY]} |")
            appendLine("| Origen propuesto correcto | $origin |")
            val times = results.map { it.millis }.sorted()
            appendLine("| Tiempo detección + segmentación (JVM) | mediana ${times[times.size / 2]} ms, máximo ${times.last()} ms |")
            appendLine()
            appendLine("## Por ángulo")
            appendLine()
            appendLine("| Ángulo | Detección | Vacía/no vacía |")
            appendLine("|---|---|---|")
            byAngle.forEach { (angle, counters) -> appendLine("| $angle | ${counters.first} | ${counters.second} |") }
            appendLine()
            appendLine("## Fallos")
            appendLine()
            if (failures.isEmpty()) appendLine("Ninguno.") else failures.forEach { appendLine("- $it") }
        }
        File("build/reports/vision").apply { mkdirs() }.resolve("metrics.md").writeText(report)
        println(report)

        // Umbrales de la Fase 2 (06-plan-fase-2.md): detección ≥ 90 %; vacías y origen con el nivel ya alcanzado.
        assertTrue(detection.rate >= 0.90, "Detección por debajo del 90 %: $detection")
        assertTrue(emptiness.rate >= 0.99, "Celdas vacías por debajo del 99 %: $emptiness")
        assertTrue(origin.rate >= 0.98, "Origen por debajo del 98 %: $origin")
    }

    /** Mismo criterio que la herramienta de etiquetado. */
    private fun proposedOrigin(cell: Cell) = if (cell.inkContrast >= PRINTED_CONTRAST) Origin.PRINTED else Origin.HANDWRITTEN

    private companion object {
        const val PRINTED_CONTRAST = 0.4
    }
}
