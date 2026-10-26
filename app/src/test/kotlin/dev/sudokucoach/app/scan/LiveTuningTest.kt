package dev.sudokucoach.app.scan

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.vision.core.live.CellVoter
import dev.sudokucoach.vision.core.live.StabilityTracker
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import kotlin.test.Test

/**
 * Ajuste de los umbrales del escáner con las secuencias grabadas (tarea 4.5). Reproduce cada
 * secuencia con cada combinación de umbrales y escribe, para las mejores, las capturas, la media
 * de sospechosas, los errores sin marcar y el tiempo hasta la captura.
 *
 * Las páginas se separan en dos grupos: con [TRAIN] se eligen los umbrales (todas capturan, 0
 * errores sin marcar y, entre esas, la menor media de sospechosas) y con el resto se comprueban.
 * No falla nunca: es una medida, se lanza a mano y sin secuencias (en la CI) se salta.
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*LiveTuningTest*" -i
 */
class LiveTuningTest {
    private class Sequence(val id: String, val format: FrameFormat, val frames: List<RecordedFrame>, val label: IntArray)

    private data class Config(
        val minArea: Double,
        val maxShift: Double,
        val stillMillis: Long,
        val maxReads: Int,
        val clearShare: Double,
        val combine: CellVoter.Combine,
        val faintInk: Double,
    ) {
        fun scanner() = LiveScanner(
            StabilityTracker(maxShift = maxShift, stillMillis = stillMillis),
            CellVoter(minReads = 3, maxReads = maxReads, clearShare = clearShare, combine = combine, faintInkShare = faintInk),
            minAreaFraction = minArea,
        )
    }

    /** Umbral de confianza: [handwrittenFour] para los 4 manuscritos (casi siempre 1 mal leídos) y [rest] para los demás. */
    private data class Threshold(val handwrittenFour: Float, val rest: Float) {
        override fun toString() = String.format(Locale.ROOT, "4 manuscrito %.2f, resto %.2f", handwrittenFour, rest)
    }

    /** Resultado de una configuración en un grupo de secuencias, con el umbral de confianza [threshold]. */
    private data class Score(val captured: Int, val total: Int, val suspicious: Double, val unflagged: Int, val maxMillis: Long, val medianMillis: Long, val threshold: Threshold) {
        val valid get() = captured == total && unflagged == 0

        override fun toString() = String.format(
            Locale.ROOT, "capturan %d/%d, sospechosas %.1f, sin marcar %d, tiempo mediana %d máx %d ms (umbral %s)",
            captured, total, suspicious, unflagged, medianMillis, maxMillis, threshold,
        )
    }

    private fun load(): List<Sequence> {
        val root = File(requireNotNull(System.getProperty("sudokucoach.sequences")))
        return root.listFiles { f -> File(f, "frames.txt").exists() && File(f, "label.txt").exists() }.orEmpty().sortedBy { it.name }.map { dir ->
            val (format, frames) = FrameLog.parse(File(dir, "frames.txt").readText())
            val label = File(dir, "label.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.take(9)
                .joinToString("").map { if (it == '.') 0 else it.digitToInt() }.toIntArray()
            Sequence(dir.name, format, frames, label)
        }
    }

    /** Sospechosas como en `ReviewBoard`, con el umbral de confianza como parámetro. */
    private fun suspicious(board: ScannedBoard, threshold: Threshold): Set<Int> {
        val repeated = UNITS.flatMap { unit ->
            unit.filter { c -> board.values[c] != 0 && unit.count { board.values[it] == board.values[c] } > 1 }
        }.toSet()
        return (0 until 81).filter { c ->
            val limit = if (board.values[c] == 4 && board.origins[c] == DigitOrigin.USER) threshold.handwrittenFour else threshold.rest
            (board.inked[c] && board.values[c] == 0) || (board.values[c] != 0 && board.confidences[c] < limit) || c in repeated
        }.toSet()
    }

    private fun score(config: Config, sequences: List<Sequence>, threshold: Threshold): Score {
        val boards = sequences.mapNotNull { s -> replay(s.frames, s.format, config.scanner()).board?.let { s to it } }
        var unflagged = 0
        var total = 0
        for ((s, board) in boards) {
            val flagged = suspicious(board, threshold)
            unflagged += (0 until 81).count { board.values[it] != s.label[it] && it !in flagged }
            total += flagged.size
        }
        val times = boards.map { it.second.millis }.sorted()
        return Score(
            boards.size, sequences.size, if (boards.isEmpty()) 0.0 else total.toDouble() / boards.size, unflagged,
            times.lastOrNull() ?: 0, times.getOrElse(times.size / 2) { 0 }, threshold,
        )
    }

    @Test
    fun ajuste() {
        val all = load()
        assumeTrue("No hay secuencias reproducibles", all.isNotEmpty())
        val train = all.filter { it.id in TRAIN }
        val check = all.filter { it.id !in TRAIN }
        println("Ajuste: ${train.map { it.id }}\nComprobación: ${check.map { it.id }}")

        val base = Config(0.25, 0.02, 300, 5, 0.6, CellVoter.Combine.BEST, 1.0)
        val old = Threshold(0.5f, 0.5f)
        println("Umbrales de 4.3 → ajuste: ${score(base, train, old)}; comprobación: ${score(base, check, old)}")

        val configs = buildList {
            for (minArea in listOf(0.10, 0.15, 0.20)) for (maxShift in listOf(0.03, 0.05, 0.08, 0.12, 0.16))
                for (still in listOf(200L, 300L)) for (maxReads in listOf(4, 5, 7)) for (clear in listOf(0.6, 0.8))
                    for (combine in CellVoter.Combine.entries) for (faint in listOf(1.0, 0.1, 0.2, 0.3))
                        add(Config(minArea, maxShift, still, maxReads, clear, combine, faint))
        }
        val scored = configs.flatMap { c -> THRESHOLDS.map { t -> c to score(c, train, t) } }
        val valid = scored.filter { it.second.valid }.sortedWith(compareBy({ it.second.suspicious }, { it.second.medianMillis }))
        println("${configs.size} configuraciones × ${THRESHOLDS.size} umbrales; válidas en el ajuste: ${valid.size}")
        for ((c, s) in valid.take(5)) report(c, s, train, check)
        for (combine in CellVoter.Combine.entries) for (t in THRESHOLDS.filter { it.handwrittenFour == it.rest || it.handwrittenFour == 0.6f }) {
            println("Mejor con $combine y umbral $t:")
            valid.firstOrNull { it.first.combine == combine && it.second.threshold == t }?.let { (c, s) -> report(c, s, train, check) } ?: println("    ninguna válida")
        }
        // Sin celdas dudosas, para ver cuánto aportan: las que capturan todo, con menos errores sin marcar.
        println("Mejor sin celdas dudosas:")
        scored.filter { it.first.faintInk == 1.0 && it.second.captured == it.second.total }
            .minWithOrNull(compareBy({ it.second.unflagged }, { it.second.suspicious }))?.let { (c, s) -> report(c, s, train, check) }
    }

    private fun report(c: Config, s: Score, train: List<Sequence>, check: List<Sequence>) {
        println("$c\n    ajuste: $s, ${margin(c, train)}\n    comprobación: ${score(c, check, s.threshold)}, ${margin(c, check)}")
    }

    /** La confianza votada más alta de un dígito equivocado: el margen hasta el umbral. */
    private fun margin(c: Config, sequences: List<Sequence>): String {
        val boards = sequences.mapNotNull { s -> replay(s.frames, s.format, c.scanner()).board?.let { s to it } }
        fun max(four: Boolean) = boards.flatMap { (s, b) ->
            (0 until 81).filter { b.values[it] != 0 && b.values[it] != s.label[it] && (b.values[it] == 4 && b.origins[it] == DigitOrigin.USER) == four }
                .map { b.confidences[it] }
        }.maxOrNull() ?: 0f
        return String.format(Locale.ROOT, "confianza máx. de un error: 4 manuscrito %.2f, resto %.2f", max(true), max(false))
    }

    private companion object {
        val TRAIN = setOf(
            "S20261025-201411", "S20261025-205500", "S20261025-205516", "S20261025-205539",
            "S20261025-205600", "S20261025-205621", "S20261025-205642",
        )
        val THRESHOLDS = listOf(0.3f, 0.4f, 0.5f).map { Threshold(it, it) } +
            listOf(0.5f, 0.6f).flatMap { four -> listOf(0.25f, 0.3f, 0.35f, 0.4f).map { Threshold(four, it) } }
        val UNITS: List<List<Int>> = (0 until 9).map { r -> (0 until 9).map { r * 9 + it } } +
            (0 until 9).map { c -> (0 until 9).map { it * 9 + c } } +
            (0 until 9).map { b -> (0 until 9).map { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 } }
    }
}
