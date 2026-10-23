package dev.sudokucoach.app.state

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.hint.HintState
import dev.sudokucoach.app.review.ReviewBoard
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Placement
import kotlin.math.roundToInt

/**
 * Tablero revisado y pista como texto, para guardarlos en el `SavedStateHandle` y sobrevivir al
 * cierre del proceso (tarea 3.8). Formato por campos separados por `|`, legible al depurar:
 *
 * - revisión: valores (81 cifras, 0 = vacía) | origen (81 letras: G impreso, U manuscrito, ? sin
 *   clasificar) | confianza en centésimas (81 números separados por comas) | tinta (81 cifras 0/1);
 * - pista: valores | candidatos (81 máscaras en hexadecimal de 3 cifras) | etapa | aplicado (0/1) |
 *   dígito revelado (`celda:dígito`, o `-`).
 *
 * El paso de la pista no se guarda: se recalcula con el motor, que es determinista, sobre el tablero
 * sin aplicar (`HintState.grid`). La foto tampoco se guarda.
 */
object StateCodec {
    fun encode(board: ReviewBoard): String = listOf(
        board.values.joinToString(""),
        board.origins.joinToString("") { ORIGIN_CODES.getValue(it).toString() },
        board.confidences.joinToString(",") { (it * 100).roundToInt().toString() },
        board.inked.joinToString("") { if (it) "1" else "0" },
    ).joinToString(SEPARATOR)

    fun decodeReview(text: String): ReviewBoard {
        val (values, origins, confidences, inked) = fields(text, 4)
        return ReviewBoard(
            values = digits(values),
            origins = cells(origins).map { code -> ORIGIN_CODES.entries.first { it.value == code }.key },
            confidences = confidences.split(",").map { it.toInt() / 100f }.toFloatArray().also { require(it.size == 81) },
            inked = cells(inked).map { it == '1' }.toBooleanArray(),
        )
    }

    fun encode(hint: HintState): String = listOf(
        hint.grid.values.joinToString(""),
        hint.grid.candidates.joinToString("") { it.toString(16).padStart(3, '0') },
        hint.level.toString(),
        if (hint.applied) "1" else "0",
        hint.revealed?.let { "${it.cell}:${it.digit}" } ?: "-",
    ).joinToString(SEPARATOR)

    fun decodeHint(text: String, engine: HintEngine = HintEngine()): HintState {
        val (values, candidates, level, applied, revealed) = fields(text, 5)
        require(candidates.length == 81 * 3) { "Se esperaban 81 máscaras de candidatos" }
        val grid = Grid(digits(values), IntArray(81) { candidates.substring(it * 3, it * 3 + 3).toInt(16) })
        val step = engine.nextStep(grid)
        return HintState(
            grid = grid,
            step = step,
            level = if (step == null) 1 else level.toInt(),
            applied = step != null && applied == "1",
            revealed = revealed.takeIf { it != "-" }?.split(":")?.let { (cell, digit) -> Placement(cell.toInt(), digit.toInt()) },
        )
    }

    private const val SEPARATOR = "|"

    private val ORIGIN_CODES = mapOf(DigitOrigin.GIVEN to 'G', DigitOrigin.USER to 'U', DigitOrigin.UNKNOWN to '?')

    private fun fields(text: String, count: Int): List<String> =
        text.split(SEPARATOR).also { require(it.size == count) { "Se esperaban $count campos y hay ${it.size}" } }

    private fun cells(field: String): List<Char> = field.toList().also { require(it.size == 81) { "Se esperaban 81 celdas" } }

    private fun digits(field: String): IntArray = cells(field).map { it.digitToInt() }.toIntArray()
}
