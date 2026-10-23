package dev.sudokucoach.app

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.scan.VisionPhotoScanner
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Units
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prueba de extremo a extremo de la Fase 3 (tarea 3.9), sin la interfaz: lector real → tablero de
 * revisión → corregir solo las celdas sospechosas → validación (D-19) → primera pista. Es el
 * criterio de la fase: "se llega a la primera pista corrigiendo a mano como mucho las celdas que la
 * app marca como sospechosas".
 *
 * Usa las fotos 001 y 016 de los assets y, si se han copiado al móvil, los 30 originales de la cámara
 * (ver `PhotoScanTest`). Escribe una línea por foto con la etiqueta `EndToEndTest` del log.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun label(id: String): IntArray = testContext.assets.open("$id.txt").bufferedReader().readLines()
        .filter { !it.startsWith("#") }.take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }.toIntArray()

    private fun shellStream(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))

    /** Resultado de una foto, para el resumen. */
    private data class Run(val id: String, val millis: Long, val suspicious: Int, val corrected: Int, val unflagged: List<Int>, val hintOk: Boolean, val technique: String)

    private fun run(scanner: VisionPhotoScanner, id: String, file: File): Run {
        val result = runBlocking { scanner.scan(Uri.fromFile(file).toString()) }
        val scanned = (result as? ScanResult.Read)?.board ?: throw AssertionError("$id: lectura fallida: $result")
        val expected = label(id)
        var board = ReviewBoard.from(scanned)
        val wrong = (0 until 81).filter { board.values[it] != expected[it] }
        val suspicious = board.suspicious

        // El usuario revisa las sospechosas: escribe el dígito correcto (o confirma el leído).
        for (cell in suspicious.sorted()) board = board.edit(cell, expected[cell])

        val validation = board.validate()
        val hint = if (validation.allowsHints) HintState.start(board.values).step else null
        val expectedStep = HintEngine().nextStep(Grid.fromValues(expected))
        val hintOk = validation.allowsHints && hint == expectedStep
        return Run(id, scanned.millis, suspicious.size, wrong.size, wrong - suspicious, hintOk, expectedStep?.technique?.name ?: "—")
    }

    @Test
    fun deLaFotoALaPrimeraPista() {
        val runs = mutableListOf<Run>()
        VisionPhotoScanner(appContext.contentResolver).use { scanner ->
            for (id in listOf("001", "016")) {
                val file = File(appContext.cacheDir, "e2e-$id.jpg")
                testContext.assets.open("$id.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
                runs += run(scanner, id, file).copy(id = "$id-conjunto").also { file.delete() }
            }
            val raw = shellStream("ls $RAW_DIR").bufferedReader().readText().lines().map { it.trim() }
                .filter { it.endsWith(".jpg") }.map { it.removeSuffix(".jpg") }.sorted()
            for (id in raw) {
                val file = File(appContext.cacheDir, "e2e-raw-$id.jpg")
                file.outputStream().use { out -> shellStream("cat $RAW_DIR/$id.jpg").use { it.copyTo(out) } }
                runs += run(scanner, id, file).also { file.delete() }
            }
        }
        for (r in runs) {
            Log.i(TAG, "${r.id}: ${r.millis} ms, ${r.suspicious} sospechosas, ${r.corrected} por corregir, sin marcar ${r.unflagged.map { Units.cellName(it) }}, pista ${if (r.hintOk) "ok" else "MAL"} (${r.technique})")
        }
        Log.i(TAG, "${runs.size} fotos; pista correcta en ${runs.count { it.hintOk }}; sospechosas en total ${runs.sumOf { it.suspicious }}, por corregir ${runs.sumOf { it.corrected }}; mediana ${runs.map { it.millis }.sorted()[runs.size / 2]} ms")

        val unflagged = runs.filter { it.unflagged.isNotEmpty() }
        assertTrue("Lecturas erróneas que la app no marca: ${unflagged.map { "${it.id} ${it.unflagged.map(Units::cellName)}" }}", unflagged.isEmpty())
        assertEquals("Fotos sin la primera pista correcta: ${runs.filter { !it.hintOk }.map { it.id }}", runs.size, runs.count { it.hintOk })
    }

    private companion object {
        const val TAG = "EndToEndTest"
        const val RAW_DIR = "/data/local/tmp/sudoku-raw"
    }
}
