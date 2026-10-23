package dev.sudokucoach.app.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sudokucoach.app.R
import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.ui.theme.PlexSans
import dev.sudokucoach.app.ui.theme.SudokuColors
import dev.sudokucoach.app.ui.theme.SudokuTheme

/**
 * Tablero de sudoku (tarea 3.2, D-12): cuadrado, a todo el ancho disponible.
 *
 * Todo se dibuja en una sola pasada; encima hay una rejilla de 81 cajas transparentes que solo
 * aportan el toque y un nodo de accesibilidad por celda ("Fila 3, columna 5: 7, impreso").
 *
 * @param onCellClick índice de la celda tocada (0..80); `null` si el tablero no es interactivo.
 */
@Composable
fun SudokuBoard(board: BoardUi, modifier: Modifier = Modifier, onCellClick: ((Int) -> Unit)? = null) {
    val colors = SudokuTheme.colors
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawWithCache {
                val glyphs = BoardGlyphs.measure(measurer, cellSize = size.width / 9f, density = this)
                onDrawBehind { drawBoard(board, colors, glyphs) }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 9) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        CellTarget(index, board.cells[index], onCellClick, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun CellTarget(index: Int, cell: CellUi, onCellClick: ((Int) -> Unit)?, modifier: Modifier) {
    val description = cellDescription(index, cell)
    val clickable = if (onCellClick != null) Modifier.clickable(role = Role.Button) { onCellClick(index) } else Modifier
    Box(
        modifier
            .then(clickable)
            .semantics {
                contentDescription = description
                selected = CellDecoration.SELECTED in cell.decorations
                testTag = "cell-$index"
            },
    )
}

@Composable
private fun cellDescription(index: Int, cell: CellUi): String {
    val row = index / 9 + 1
    val col = index % 9 + 1
    val base = when {
        cell.value != 0 -> {
            val origin = when (cell.origin) {
                DigitOrigin.GIVEN -> stringResource(R.string.cell_origin_given)
                DigitOrigin.USER -> stringResource(R.string.cell_origin_user)
                DigitOrigin.UNKNOWN -> null
            }
            if (origin != null) stringResource(R.string.cell_digit_origin, row, col, cell.value, origin)
            else stringResource(R.string.cell_digit, row, col, cell.value)
        }
        cell.placement != null -> stringResource(R.string.cell_placement, row, col, cell.placement)
        else -> stringResource(R.string.cell_empty, row, col)
    }
    val extras = buildList {
        if (CellDecoration.SUSPICIOUS in cell.decorations) add(stringResource(R.string.cell_suspicious))
        if (CellDecoration.ERROR in cell.decorations) add(stringResource(R.string.cell_error))
        if (CellDecoration.PATTERN in cell.decorations) add(stringResource(R.string.cell_pattern))
        val kept = cell.candidates - cell.eliminated
        if (cell.value == 0 && kept.isNotEmpty()) add(stringResource(R.string.cell_candidates, kept.sorted().joinToString(", ")))
        if (cell.eliminated.isNotEmpty()) add(stringResource(R.string.cell_eliminated, cell.eliminated.sorted().joinToString(", ")))
    }
    return (listOf(base) + extras).joinToString(". ")
}

/** Los dígitos ya medidos para un tamaño de celda: se reutilizan en cada dibujo. */
private class BoardGlyphs(
    val given: List<TextLayoutResult>,
    val user: List<TextLayoutResult>,
    val placement: List<TextLayoutResult>,
    val candidate: List<TextLayoutResult>,
) {
    companion object {
        /** Los tamaños salen de la celda en px; `toSp` descuenta la escala de fuente, así que no crecen con ella. */
        fun measure(measurer: TextMeasurer, cellSize: Float, density: Density): BoardGlyphs {
            fun digits(weight: FontWeight, fraction: Float): List<TextLayoutResult> {
                val style = TextStyle(fontFamily = PlexSans, fontWeight = weight, fontSize = with(density) { (cellSize * fraction).toSp() })
                return (1..9).map { measurer.measure(it.toString(), style) }
            }
            return BoardGlyphs(
                given = digits(FontWeight.Bold, DIGIT_SIZE),
                user = digits(FontWeight.Normal, DIGIT_SIZE),
                placement = digits(FontWeight.Medium, DIGIT_SIZE),
                candidate = digits(FontWeight.Normal, CANDIDATE_SIZE),
            )
        }
    }
}

private fun DrawScope.drawBoard(board: BoardUi, colors: SudokuColors, glyphs: BoardGlyphs) {
    val cell = size.width / 9f
    drawRect(colors.background)

    for ((index, c) in board.cells.withIndex()) {
        val topLeft = Offset((index % 9) * cell, (index / 9) * cell)
        val decorations = c.decorations
        if (CellDecoration.SELECTED in decorations || CellDecoration.PATTERN in decorations) {
            drawRect(colors.accentSoft, topLeft, Size(cell, cell))
        }
        when {
            c.value != 0 -> {
                val layouts = if (c.origin == DigitOrigin.GIVEN) glyphs.given else glyphs.user
                val color = if (CellDecoration.ERROR in decorations) colors.accent else colors.ink
                drawCentered(layouts[c.value - 1], color, topLeft, cell)
            }
            c.placement != null -> drawCentered(glyphs.placement[c.placement - 1], colors.accent, topLeft, cell)
            else -> drawCandidates(c, colors, glyphs, topLeft, cell)
        }
    }

    drawGridLines(colors, cell)

    // Los contornos van encima de las líneas, metidos un poco en la celda para que no se confundan con ellas.
    for ((index, c) in board.cells.withIndex()) {
        val topLeft = Offset((index % 9) * cell, (index / 9) * cell)
        val inset = OUTLINE_WIDTH.toPx() * 1.5f
        val rectTopLeft = topLeft + Offset(inset, inset)
        val rectSize = Size(cell - 2 * inset, cell - 2 * inset)
        val solid = CellDecoration.PLACEMENT in c.decorations || CellDecoration.ERROR in c.decorations
        when {
            solid -> drawRect(colors.accent, rectTopLeft, rectSize, style = Stroke(OUTLINE_WIDTH.toPx()))
            CellDecoration.SUSPICIOUS in c.decorations -> {
                val dash = cell / 10f
                drawRect(
                    colors.accent, rectTopLeft, rectSize,
                    style = Stroke(OUTLINE_WIDTH.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))),
                )
            }
        }
    }
}

/** Mini-rejilla 3×3: el candidato d ocupa la posición (d−1) en orden de lectura. */
private fun DrawScope.drawCandidates(c: CellUi, colors: SudokuColors, glyphs: BoardGlyphs, topLeft: Offset, cell: Float) {
    val sub = cell / 3f
    for (digit in c.candidates) {
        val subTopLeft = topLeft + Offset(((digit - 1) % 3) * sub, ((digit - 1) / 3) * sub)
        val eliminated = digit in c.eliminated
        val layout = glyphs.candidate[digit - 1]
        drawCentered(layout, if (eliminated) colors.inkMuted else colors.ink, subTopLeft, sub)
        if (eliminated) {
            val y = subTopLeft.y + sub / 2f
            val half = layout.size.width * 0.8f
            val x = subTopLeft.x + sub / 2f
            drawLine(colors.inkMuted, Offset(x - half, y), Offset(x + half, y), strokeWidth = STRIKE_WIDTH.toPx())
        }
    }
}

private fun DrawScope.drawGridLines(colors: SudokuColors, cell: Float) {
    val thin = THIN_LINE.toPx()
    val thick = THICK_LINE.toPx()
    for (i in 1 until 9) {
        if (i % 3 == 0) continue
        val p = i * cell
        drawLine(colors.gridLine, Offset(p, 0f), Offset(p, size.height), thin)
        drawLine(colors.gridLine, Offset(0f, p), Offset(size.width, p), thin)
    }
    for (i in 0..9 step 3) {
        // Las líneas exteriores se meten media anchura para que no queden cortadas por el borde.
        val p = (i * cell).coerceIn(thick / 2f, size.width - thick / 2f)
        drawLine(colors.ink, Offset(p, 0f), Offset(p, size.height), thick)
        drawLine(colors.ink, Offset(0f, p), Offset(size.width, p), thick)
    }
}

private fun DrawScope.drawCentered(layout: TextLayoutResult, color: Color, topLeft: Offset, box: Float) {
    val offset = Offset(topLeft.x + (box - layout.size.width) / 2f, topLeft.y + (box - layout.size.height) / 2f)
    drawText(layout, color = color, topLeft = offset)
}

private val THIN_LINE: Dp = 1.dp
private val THICK_LINE: Dp = 2.dp
private val OUTLINE_WIDTH: Dp = 2.dp
private val STRIKE_WIDTH: Dp = 1.dp

/** Tamaño de letra del dígito y de los candidatos, como fracción del lado de la celda. */
private const val DIGIT_SIZE = 0.62f
private const val CANDIDATE_SIZE = 0.26f
