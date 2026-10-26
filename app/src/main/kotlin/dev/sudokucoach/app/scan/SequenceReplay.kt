package dev.sudokucoach.app.scan

import dev.sudokucoach.app.camera.unrotate
import dev.sudokucoach.vision.core.live.CellReading
import dev.sudokucoach.vision.core.live.Corner
import java.util.Locale

/**
 * Un fotograma de una secuencia grabada (D-22, tarea 4.4) ya pasado por el lector real en el móvil:
 * cuándo llegó, cuánto tardó la detección, las esquinas (`null` sin tablero) y, si hay tablero, la
 * lectura completa y lo que tardó. Con esto se reproduce el escáner sin cámara ni ML Kit.
 */
data class RecordedFrame(
    val timeMillis: Long,
    val detectMillis: Long,
    val corners: List<Corner>?,
    val readMillis: Long,
    val reading: List<CellReading>?,
)

/** Tamaño de los fotogramas en gris (ya girados) y el giro aplicado, de `session.txt`. */
data class FrameFormat(val width: Int, val height: Int, val rotationDegrees: Int)

/** Resultado de reproducir una secuencia: el tablero capturado (o `null`) y cuántas lecturas votaron. */
data class ReplayResult(val board: ScannedBoard?, val reads: Int, val analyzed: Int)

/**
 * Reproduce una secuencia por [scanner] como en vivo (tarea 4.4): un solo hilo de análisis, así que
 * los fotogramas que llegan mientras se detecta o se lee el anterior se descartan, con los tiempos
 * medidos en el móvil. Se para en la captura.
 */
fun replay(frames: List<RecordedFrame>, format: FrameFormat, scanner: LiveScanner = LiveScanner()): ReplayResult {
    var busyUntil = Long.MIN_VALUE
    var analyzed = 0
    for (frame in frames) {
        if (frame.timeMillis < busyUntil) continue
        analyzed++
        val detected = frame.timeMillis + frame.detectMillis
        busyUntil = detected
        val detection = frame.corners?.let { corners ->
            Detection(corners, corners.map { unrotate(it.x, it.y, format.width, format.height, format.rotationDegrees) })
        }
        val read = scanner.onFrame(detection, detected)
        val reading = frame.reading
        if (!read || reading == null) continue
        busyUntil = detected + frame.readMillis
        scanner.onReading(reading, busyUntil)?.let { return ReplayResult(it, scanner.reads, analyzed) }
    }
    return ReplayResult(null, scanner.reads, analyzed)
}

/**
 * `frames.txt` de una secuencia: una cabecera `# size=1080x1920 rotation=90` y una línea por
 * fotograma: tiempo, ms de detección, esquinas (`x,y` × 4, o `-`), ms de lectura y, si hay lectura,
 * dígitos, confianzas (milésimas, separadas por comas), tinta (`#`/`.`) y origen (`P`/`.`).
 */
object FrameLog {
    fun format(format: FrameFormat, frames: List<RecordedFrame>): String = buildString {
        appendLine("# size=${format.width}x${format.height} rotation=${format.rotationDegrees}")
        for (f in frames) {
            append(f.timeMillis).append(' ').append(f.detectMillis).append(' ')
            append(f.corners?.joinToString(",") { "%.1f,%.1f".format(Locale.ROOT, it.x, it.y) } ?: "-")
            append(' ').append(f.readMillis)
            f.reading?.let { cells ->
                append(' ').append(cells.joinToString("") { it.digit.toString() })
                append(' ').append(cells.joinToString(",") { (it.confidence * 1000).toInt().toString() })
                append(' ').append(cells.joinToString("") { if (it.inked) "#" else "." })
                append(' ').append(cells.joinToString("") { if (it.printed) "P" else "." })
            }
            appendLine()
        }
    }

    fun parse(text: String): Pair<FrameFormat, List<RecordedFrame>> {
        val lines = text.lines().filter { it.isNotBlank() }
        val header = lines.first().removePrefix("#").trim().split(" ").associate { it.substringBefore("=") to it.substringAfter("=") }
        val (width, height) = header.getValue("size").split("x").map { it.toInt() }
        val format = FrameFormat(width, height, header.getValue("rotation").toInt())
        val frames = lines.drop(1).map { line ->
            val p = line.split(" ")
            val corners = p[2].takeIf { it != "-" }?.split(",")?.map { it.toDouble() }?.chunked(2)?.map { Corner(it[0], it[1]) }
            val reading = if (p.size > 4) {
                val confidences = p[5].split(",").map { it.toInt() / 1000f }
                List(81) { CellReading(p[4][it].digitToInt(), confidences[it], inked = p[6][it] == '#', printed = p[7][it] == 'P') }
            } else {
                null
            }
            RecordedFrame(p[0].toLong(), p[1].toLong(), corners, p[3].toLong(), reading)
        }
        return format to frames
    }
}
