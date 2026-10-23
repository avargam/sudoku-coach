package dev.sudokucoach.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.vision.core.BoardDetector
import dev.sudokucoach.vision.toGrayMat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Decodificación de fotos de galería y lectura completa en el móvil (tarea 3.3), con la foto 001. */
@RunWith(AndroidJUnit4::class)
class PhotoScanTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = appContext.contentResolver

    private fun original(): Bitmap = testContext.assets.open("001.jpg").use { BitmapFactory.decodeStream(it) }

    /**
     * Como sale de la cámara del A57: 4000×3000, apaisada en los píxeles y con la orientación en el
     * EXIF (girar 90° a la derecha para verla derecha).
     */
    private fun cameraLikePhoto(): Uri {
        val upright = original()
        val stored = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply {
            postRotate(-90f)
            postScale(2.5f, 2.5f)
        }, true)
        assertEquals(4000, stored.width)
        assertEquals(3000, stored.height)
        val file = File(appContext.cacheDir, "camera-like.jpg")
        file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    @Test
    fun imageDecoderEnderezaYReduceLaFoto() = checkDecoded(PhotoDecoder.decodeWithImageDecoder(resolver, cameraLikePhoto()))

    @Test
    fun laViaDeAndroid8EnderezaYReduceLaFoto() = checkDecoded(PhotoDecoder.decodeLegacy(resolver, cameraLikePhoto()))

    private fun checkDecoded(decoded: Bitmap) {
        assertEquals(1200, decoded.width)
        assertEquals(1600, decoded.height)
        // Derecha y no cabeza abajo: se parece a la original mucho más que a la original girada 180°.
        val upright = original()
        val upsideDown = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(180f) }, true)
        val same = difference(decoded, upright)
        val flipped = difference(decoded, upsideDown)
        assertTrue("diferencia con la original %.1f, con la girada %.1f".format(same, flipped), same < 10 && flipped > 2 * same)
        assertNotNull("No se detecta el tablero", BoardDetector().detect(decoded.toGrayMat()))
    }

    @Test
    fun unaFotoPequenaNoSeAmplia() {
        val file = File(appContext.cacheDir, "small.jpg")
        file.outputStream().use { Bitmap.createScaledBitmap(original(), 600, 800, true).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val decoded = PhotoDecoder.decode(resolver, Uri.fromFile(file))
        assertEquals(600, decoded.width)
        assertEquals(800, decoded.height)
    }

    @Test
    fun leeLaFoto001ComoSuEtiqueta() {
        val file = File(appContext.cacheDir, "001.jpg")
        testContext.assets.open("001.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
        val board = VisionPhotoScanner(resolver).use { scanner -> read(scanner, Uri.fromFile(file)) }
        val check = compare(board, label("001"))
        assertTrue(check.toString(), check.inkMismatches.isEmpty() && check.wrongSure.isEmpty() && check.originMismatches.isEmpty())
        assertTrue("Solo ${check.sure} lecturas seguras", check.sure >= 60)
    }

    /**
     * Los originales de la cámara (4000×3000, orientación en el EXIF), el caso real de la app. No se
     * versionan (D-17), así que el test solo se ejecuta si se han copiado al móvil:
     *
     *     tail -n +2 testdata/vision/sources.csv | tr -d '\r' | while IFS=, read id original; do
     *         adb push testdata/vision/raw/$original /data/local/tmp/sudoku-raw/$id.jpg </dev/null; done
     *
     * Se leen con el shell de adb (el único que puede leer `/data/local/tmp`) y se comparan con las
     * etiquetas. Falla si hay una lectura segura errónea que no repite dígito en su unidad, porque
     * ni la confianza ni la validación de D-19 la delatarían. Escribe el resumen en el log con la
     * etiqueta `PhotoScanTest`.
     */
    @Test
    fun leeLosOriginalesDeLaCamara() {
        val ids = shell("ls $RAW_DIR").lines().map { it.trim() }.filter { it.endsWith(".jpg") }.map { it.removeSuffix(".jpg") }.sorted()
        assumeTrue("Sin originales en $RAW_DIR", ids.isNotEmpty())
        val failures = mutableListOf<String>()
        var sure = 0
        var digits = 0
        VisionPhotoScanner(resolver).use { scanner ->
            for (id in ids) {
                val file = File(appContext.cacheDir, "raw-$id.jpg")
                file.outputStream().use { out -> shellStream("cat $RAW_DIR/$id.jpg").use { it.copyTo(out) } }
                val result = runBlocking { scanner.scan(Uri.fromFile(file).toString()) }
                file.delete()
                val board = (result as? ScanResult.Read)?.board
                if (board == null) {
                    failures += "$id: $result"
                    continue
                }
                val check = compare(board, label(id))
                sure += check.sure
                digits += check.digits
                Log.i(TAG, "$id: ${board.millis} ms, $check")
                // Una lectura segura errónea que repite un dígito en su unidad la marca la validación (D-19).
                val unflagged = check.wrongSure.filter { !repeated(board.values, it) }
                if (unflagged.isNotEmpty()) failures += "$id: lecturas seguras erróneas sin repetido en $unflagged"
            }
        }
        Log.i(TAG, "${ids.size} originales: $sure/$digits dígitos con lectura segura; fallos: $failures")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun read(scanner: VisionPhotoScanner, uri: Uri): ScannedBoard {
        val result = runBlocking { scanner.scan(uri.toString()) }
        return (result as? ScanResult.Read)?.board ?: throw AssertionError("Lectura fallida: $result")
    }

    private class Label(val digits: List<Int>, val origins: String)

    private fun label(id: String): Label {
        val lines = testContext.assets.open("$id.txt").bufferedReader().readLines().filter { !it.startsWith("#") }
        return Label(lines.take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }, lines.drop(9).take(9).joinToString(""))
    }

    /** Diferencias entre la lectura y la etiqueta, por celda (F1C1 = 0). */
    private data class Check(
        val digits: Int,
        val sure: Int,
        val inkMismatches: List<Int>,
        val wrongSure: List<Int>,
        val originMismatches: List<Int>,
    )

    private fun compare(board: ScannedBoard, label: Label): Check {
        val cells = 0 until 81
        return Check(
            digits = cells.count { label.digits[it] != 0 },
            // Las lecturas con confianza ≥ 0,5 se dan por buenas; las demás se piden al usuario (D-08).
            sure = cells.count { label.digits[it] != 0 && board.values[it] == label.digits[it] && board.confidences[it] >= 0.5f },
            inkMismatches = cells.filter { board.inked[it] != (label.digits[it] != 0) },
            wrongSure = cells.filter { board.confidences[it] >= 0.5f && board.values[it] != label.digits[it] },
            originMismatches = cells.filter {
                val expected = when (label.origins[it]) {
                    'P' -> DigitOrigin.GIVEN
                    'M' -> DigitOrigin.USER
                    else -> DigitOrigin.UNKNOWN
                }
                board.inked[it] && board.origins[it] != expected
            },
        )
    }

    /** El dígito de [cell] aparece en otra celda de su fila, columna o caja. */
    private fun repeated(values: IntArray, cell: Int): Boolean {
        val value = values[cell]
        val row = cell / 9
        val col = cell % 9
        return value != 0 && (0 until 81).any { other ->
            other != cell && values[other] == value &&
                (other / 9 == row || other % 9 == col || (other / 27 == cell / 27 && other % 9 / 3 == col / 3))
        }
    }

    private fun shellStream(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))

    private fun shell(command: String): String = shellStream(command).bufferedReader().readText()

    /** Diferencia media de gris (0–255) entre dos fotos reducidas a 60×80. */
    private fun difference(a: Bitmap, b: Bitmap): Double {
        fun gray(bitmap: Bitmap): IntArray {
            val small = Bitmap.createScaledBitmap(bitmap, 60, 80, true)
            return IntArray(60 * 80).also { small.getPixels(it, 0, 60, 0, 0, 60, 80) }
                .map { (Color.red(it) + Color.green(it) + Color.blue(it)) / 3 }.toIntArray()
        }
        val ga = gray(a)
        val gb = gray(b)
        return ga.indices.sumOf { abs(ga[it] - gb[it]) }.toDouble() / ga.size
    }

    private companion object {
        const val TAG = "PhotoScanTest"
        const val RAW_DIR = "/data/local/tmp/sudoku-raw"
    }
}
