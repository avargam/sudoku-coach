package dev.sudokucoach.app.scan

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.vision.core.live.CellReading

/**
 * Tablero leído de una foto (tarea 3.3). Solo tipos de Kotlin, sin OpenCV, para que la lógica de la
 * app (revisión, validación, estado) se pruebe en la JVM con lecturas inventadas.
 *
 * @property values 81 dígitos por filas; 0 si la celda está vacía o no se pudo leer.
 * @property origins origen de cada celda con tinta según el contraste (`Cell.printed`); `UNKNOWN` en las vacías.
 * @property confidences confianza de cada lectura: 1 en las vacías, 0 en las celdas con tinta sin lectura.
 * @property inked celdas en las que el segmentador vio un dígito, se haya leído o no.
 * @property millis tiempo de decodificación y lectura.
 */
class ScannedBoard(
    val values: IntArray,
    val origins: List<DigitOrigin>,
    val confidences: FloatArray,
    val inked: BooleanArray,
    val millis: Long,
) {
    init {
        require(values.size == 81 && origins.size == 81 && confidences.size == 81 && inked.size == 81) {
            "Un tablero tiene 81 celdas"
        }
    }
}

/**
 * Tablero a partir de las 81 celdas leídas, de una foto o votadas en vivo (tarea 4.3); [millis] es
 * el tiempo de la lectura.
 */
fun List<CellReading>.toScannedBoard(millis: Long) = ScannedBoard(
    values = IntArray(81) { this[it].digit },
    origins = map { cell ->
        when {
            !cell.inked -> DigitOrigin.UNKNOWN
            cell.printed -> DigitOrigin.GIVEN
            else -> DigitOrigin.USER
        }
    },
    confidences = FloatArray(81) { this[it].confidence },
    inked = BooleanArray(81) { this[it].inked },
    millis = millis,
)

sealed interface ScanResult {
    data class Read(val board: ScannedBoard) : ScanResult

    /** La foto se leyó, pero no hay ningún tablero de sudoku en ella. */
    data object NoBoard : ScanResult

    /** No se pudo abrir o procesar la foto. */
    data class Failed(val message: String) : ScanResult
}

/**
 * Lee el tablero de una foto. La foto se identifica por su URI como texto (`content://…`), para que
 * el `ViewModel` no dependa de clases de Android y se pueda probar en la JVM.
 */
fun interface PhotoScanner {
    suspend fun scan(photo: String): ScanResult
}
