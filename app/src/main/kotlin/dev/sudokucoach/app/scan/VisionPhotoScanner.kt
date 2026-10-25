package dev.sudokucoach.app.scan

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import dev.sudokucoach.vision.MlKitGridReader
import dev.sudokucoach.vision.core.BoardReader
import dev.sudokucoach.vision.core.BoardReading
import dev.sudokucoach.vision.core.live.toCellReadings
import dev.sudokucoach.vision.toGrayMat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * [PhotoScanner] real (tarea 3.3): decodifica la foto y la pasa por `BoardReader` con la lámina de
 * ML Kit (D-08). Se crea una vez y se cierra con el `ViewModel`, porque el cliente de ML Kit carga
 * su modelo en la primera lectura.
 */
class VisionPhotoScanner(private val resolver: ContentResolver) : PhotoScanner, Closeable {
    private val recognizer by lazy { MlKitGridReader() }
    private val reader by lazy { BoardReader(recognizer) }

    override suspend fun scan(photo: String): ScanResult = withContext(Dispatchers.Default) {
        try {
            val start = SystemClock.elapsedRealtime()
            val bitmap = PhotoDecoder.decode(resolver, Uri.parse(photo))
            val gray = bitmap.toGrayMat()
            bitmap.recycle()
            val reading = reader.read(gray)
            gray.release()
            if (reading == null) ScanResult.NoBoard else ScanResult.Read(reading.toScannedBoard(SystemClock.elapsedRealtime() - start))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun close() = recognizer.close()
}

/** Pasa la lectura a tipos de Kotlin y libera las imágenes de OpenCV, que viven fuera del montón de Java. */
private fun BoardReading.toScannedBoard(millis: Long): ScannedBoard {
    val board = toCellReadings().toScannedBoard(millis)
    scan.rectified.release()
    cells.forEach { it.digit?.release() }
    return board
}
