package dev.sudokucoach.vision.core

import org.opencv.core.Mat

/**
 * Lectura completa de una foto.
 *
 * @property scan tablero localizado y rectificado.
 * @property cells las 81 celdas segmentadas.
 * @property predictions una por celda; las vacías llevan dígito 0 y confianza 1.
 */
class BoardReading(val scan: BoardScan, val cells: List<Cell>, val predictions: List<Prediction>) {
    /** Los 81 valores leídos (0 = vacía), en el formato del `solver`. */
    val digits: IntArray get() = IntArray(81) { predictions[it].digit }
}

/**
 * Encadena detección, segmentación y reconocimiento: de una foto a 81 dígitos (Fase 2).
 * El reconocedor se inyecta, así que el mismo flujo sirve en la JVM (tests) y en Android (ML Kit).
 */
class BoardReader(
    private val recognizer: BoardRecognizer,
    private val detector: BoardDetector = BoardDetector(),
    private val segmenter: GridSegmenter = GridSegmenter(),
) {
    /** Devuelve la lectura, o `null` si no se encuentra el tablero. */
    fun read(image: Mat): BoardReading? = detector.detect(image)?.let(::read)

    /** Lee un tablero ya detectado; en vivo, el del fotograma (tarea 4.3). */
    fun read(scan: BoardScan): BoardReading {
        val cells = segmenter.segment(scan.rectified)
        return BoardReading(scan, cells, recognizer.recognize(scan.rectified, cells))
    }
}
