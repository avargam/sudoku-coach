package dev.sudokucoach.vision.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Métricas del reconocimiento de dígitos en el móvil (tarea 2.8). `DeviceMetricsTest` (módulo
 * `vision`) guarda las predicciones de cada estrategia de ML Kit en `testdata/vision/results/`; aquí se
 * comparan con las etiquetas y se escribe `build/reports/vision/device.md`. No exige umbrales: con
 * estas cifras se revisa D-08 al cerrar la fase (2.9).
 */
class DeviceMetricsReportTest {

    /** Predicciones de una estrategia para una foto: 81 dígitos (0 = sin dígito) y sus confianzas. */
    private class StrategyResult(val digits: IntArray, val confidence: FloatArray, val millis: Long)

    private class PhotoResult(val id: String, val label: PhotoLabel, val baseMillis: Long, val strategies: Map<String, StrategyResult>?)

    @Test
    fun `informe del reconocimiento en el movil`() {
        val files = File(VisionTestData.root, "results").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assumeTrue(files.isNotEmpty(), "No hay resultados del móvil en testdata/vision/results")
        val labels = VisionTestData.photos().mapNotNull { photo -> photo.label?.let { photo.id to it } }.toMap()

        val report = buildString {
            appendLine("# Reconocimiento de dígitos en el móvil")
            for (file in files) {
                val json = Json.parseToJsonElement(file.readText()).jsonObject
                val photos = json.getValue("photos").jsonArray.map { parsePhoto(it.jsonObject, labels) }
                assertTrue(photos.isNotEmpty(), "${file.name} no tiene fotos")
                appendLine()
                appendLine("## ${file.name}")
                appendLine()
                appendLine("${json["device"]?.jsonPrimitive?.content}, API ${json["sdk"]?.jsonPrimitive?.content}, OpenCV ${json["opencv"]?.jsonPrimitive?.content}. Fotos: ${photos.size}, tableros detectados: ${photos.count { it.strategies != null }}.")
                val detected = photos.filter { it.strategies != null }
                val strategies = detected.first().strategies!!.keys.sorted()
                for (name in strategies) appendStrategy(name, detected.map { it to it.strategies!!.getValue(name) })
            }
        }
        File("build/reports/vision").apply { mkdirs() }.resolve("device.md").writeText(report)
        println(report)
    }

    private fun parsePhoto(json: JsonObject, labels: Map<String, PhotoLabel>): PhotoResult {
        val id = json.getValue("id").jsonPrimitive.content
        val label = labels[id] ?: error("La foto $id del resultado no tiene etiqueta")
        if (!json.getValue("detected").jsonPrimitive.boolean) return PhotoResult(id, label, 0, null)
        val base = json.getValue("detectMs").jsonPrimitive.long + json.getValue("segmentMs").jsonPrimitive.long
        val strategies = json.filterKeys { it in STRATEGY_NAMES.keys }.mapValues { (_, value) ->
            val s = value.jsonObject
            StrategyResult(
                digits = s.getValue("digits").jsonPrimitive.content.map { if (it == '.') 0 else it.digitToInt() }.toIntArray(),
                confidence = s.getValue("confidence").jsonArray.map { it.jsonPrimitive.float }.toFloatArray(),
                millis = s.getValue("ms").jsonPrimitive.long,
            )
        }
        return PhotoResult(id, label, base, strategies)
    }

    private fun StringBuilder.appendStrategy(name: String, results: List<Pair<PhotoResult, StrategyResult>>) {
        val byOrigin = mapOf(Origin.PRINTED to Counter(), Origin.HANDWRITTEN to Counter())
        var unread = 0
        var wrong = 0
        var phantom = 0 // celda vacía con un dígito leído
        var perfectBoards = 0
        val confusions = HashMap<Pair<Int, Int>, Int>()
        val scored = mutableListOf<Pair<Float, Boolean>>() // (confianza, acierto) de cada dígito leído
        for ((photo, s) in results) {
            var perfect = true
            for (i in 0 until 81) {
                val expected = photo.label.digits[i]
                val read = s.digits[i]
                if (read != expected) perfect = false
                val origin = photo.label.origins[i]
                if (origin == Origin.EMPTY) {
                    if (read != 0) phantom++
                    continue
                }
                byOrigin.getValue(origin).add(read == expected)
                if (read == 0) unread++
                if (read != 0 && read != expected) wrong++
                if (read != expected) confusions.merge(expected to read, 1, Int::plus)
                if (read != 0) scored += s.confidence[i] to (read == expected)
            }
            if (perfect) perfectBoards++
        }
        val total = Counter(byOrigin.values.sumOf { it.ok }, byOrigin.values.sumOf { it.total })
        val times = results.map { (photo, s) -> photo.baseMillis + s.millis }.sorted()

        appendLine()
        appendLine("### ${STRATEGY_NAMES[name]}")
        appendLine()
        appendLine("| Métrica | Resultado |")
        appendLine("|---|---|")
        appendLine("| Dígitos correctos | $total |")
        appendLine("| — impresos | ${byOrigin[Origin.PRINTED]} |")
        appendLine("| — manuscritos | ${byOrigin[Origin.HANDWRITTEN]} |")
        appendLine("| Sin lectura | $unread |")
        appendLine("| Dígito equivocado | $wrong |")
        appendLine("| Dígito en celda vacía | $phantom |")
        appendLine("| Tableros sin ningún fallo | $perfectBoards/${results.size} |")
        appendLine("| Tiempo por foto (detección + segmentación + reconocimiento) | mediana ${times[times.size / 2]} ms, máximo ${times.last()} ms |")
        appendLine()
        appendLine("Confianza de los dígitos leídos (para decidir qué se marca \"por confirmar\"):")
        appendLine()
        appendLine("| Confianza ≥ | Dígitos leídos | Correctos |")
        appendLine("|---|---|---|")
        for (threshold in CONFIDENCE_THRESHOLDS) {
            val above = scored.filter { it.first >= threshold }
            appendLine("| $threshold | ${above.size} | ${Counter(above.count { it.second }, above.size)} |")
        }
        appendLine()
        appendLine("Confusiones más frecuentes (etiqueta → leído, `-` = sin lectura): " +
            confusions.entries.sortedByDescending { it.value }.take(10).joinToString(", ") { (pair, n) ->
                "${pair.first}→${if (pair.second == 0) "-" else pair.second} ($n)"
            })
    }

    private class Counter(var ok: Int = 0, var total: Int = 0) {
        fun add(correct: Boolean) {
            total++
            if (correct) ok++
        }
        override fun toString() = if (total == 0) "0/0" else "$ok/$total (%.1f %%)".format(ok * 100.0 / total)
    }

    private companion object {
        val STRATEGY_NAMES = mapOf("cell" to "Celda a celda (MlKitDigitRecognizer)", "grid" to "Cuadrícula entera (MlKitGridReader)")
        val CONFIDENCE_THRESHOLDS = listOf(0.0f, 0.5f, 0.7f, 0.9f)
    }
}
