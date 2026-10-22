package dev.sudokucoach.vision

import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.vision.core.BoardDetector
import dev.sudokucoach.vision.core.BoardRecognizer
import dev.sudokucoach.vision.core.GridSegmenter
import dev.sudokucoach.vision.core.Prediction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import java.io.File

/**
 * Pasa las fotos de prueba por el pipeline completo en el móvil, con las dos estrategias de ML Kit
 * (tarea 2.8), y guarda las predicciones y los tiempos en `device-metrics.json`. Las métricas se
 * calculan en la JVM contra las etiquetas (`DeviceMetricsReportTest` en `vision-core`).
 *
 * Tarda alrededor de un minuto, así que solo se ejecuta si se pide con el argumento `metrics=true`
 * (ver `vision/README.md`).
 */
@RunWith(AndroidJUnit4::class)
class DeviceMetricsTest {

    @Test
    fun prediccionesSobreLasFotos() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Solo con -Pandroid.testInstrumentationRunnerArguments.metrics=true", arguments.getString("metrics") == "true")
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val ids = assets.list("photos").orEmpty().filter { it.endsWith(".jpg") }.sorted().map { it.removeSuffix(".jpg") }
        assumeTrue("No hay fotos en los assets", ids.isNotEmpty())

        OpenCvAndroid.ensureLoaded()
        val detector = BoardDetector()
        val segmenter = GridSegmenter()
        val photos = JSONArray()
        MlKitDigitRecognizer().use { cellRecognizer ->
            MlKitGridReader().use { gridReader ->
                // Calentamiento: la primera llamada a ML Kit carga el modelo y no debe contar en los tiempos.
                cellRecognizer.recognize(SyntheticImages.digit(5).toGrayMat())
                val byCellReader = BoardRecognizer.cellByCell(cellRecognizer)

                for (id in ids) {
                    val image = assets.open("photos/$id.jpg").use { BitmapFactory.decodeStream(it) }.toGrayMat()
                    val (scan, detectMs) = timed { detector.detect(image) }
                    val photo = JSONObject().put("id", id).put("detected", scan != null).put("detectMs", detectMs)
                    if (scan != null) {
                        val (cells, segmentMs) = timed { segmenter.segment(scan.rectified) }
                        val (byCell, cellMs) = timed { byCellReader.recognize(scan.rectified, cells) }
                        val (byGrid, gridMs) = timed { gridReader.recognize(scan.rectified, cells) }
                        photo.put("segmentMs", segmentMs)
                            .put("empty", cells.joinToString("") { if (it.empty) "1" else "0" })
                            .put("cell", strategy(byCell, cellMs))
                            .put("grid", strategy(byGrid, gridMs))
                    }
                    photos.put(photo)
                    Log.i(TAG, "$id: $photo".take(200))
                }
            }
        }

        val result = JSONObject()
            .put("device", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("opencv", Core.VERSION)
            .put("photos", photos)
        val file = File(outputDir(), "device-metrics.json")
        file.writeText(result.toString())
        Log.i(TAG, "Escrito ${file.absolutePath}")
    }

    /** Dígitos como texto de 81 caracteres (`.` = sin dígito) y confianzas con dos decimales. */
    private fun strategy(predictions: List<Prediction>, millis: Long) = JSONObject()
        .put("ms", millis)
        .put("digits", predictions.joinToString("") { if (it.digit == 0) "." else "${it.digit}" })
        .put("confidence", JSONArray(predictions.map { Math.round(it.confidence * 100) / 100.0 }))

    /**
     * Directorio que AGP descarga al terminar los tests ("additional test output") en
     * `build/outputs/connected_android_test_additional_output/`.
     */
    private fun outputDir(): File {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath
        return File(dir).apply { mkdirs() }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val value = block()
        return value to (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val TAG = "DeviceMetrics"
    }
}
