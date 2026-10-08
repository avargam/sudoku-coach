package dev.sudokucoach.vision

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/** Un carácter leído por ML Kit que se interpreta como dígito, con su caja y su confianza. */
internal class DigitSymbol(val digit: Int, val box: Rect, val confidence: Float)

/**
 * Lecturas de ML Kit que corresponden a dígitos. ML Kit reconoce texto general, así que a veces
 * devuelve letras parecidas a un dígito (I/l → 1, S → 5, Z → 2...); como en un sudoku solo puede
 * haber dígitos del 1 al 9, se reinterpretan. El 0 y la O no son válidos y se descartan.
 */
internal fun Text.digitSymbols(): List<DigitSymbol> =
    textBlocks.flatMap { it.lines }.flatMap { it.elements }.flatMap { it.symbols }.mapNotNull { symbol ->
        val digit = symbol.text.singleOrNull()?.let(::digitOf) ?: return@mapNotNull null
        val box = symbol.boundingBox ?: return@mapNotNull null
        DigitSymbol(digit, box, symbol.confidence.coerceIn(0f, 1f))
    }

internal fun digitOf(c: Char): Int? = when (c) {
    in '1'..'9' -> c - '0'
    'I', 'l', 'i', '|', '!', 'J' -> 1
    'Z', 'z' -> 2
    'A' -> 4
    'S', 's', '$' -> 5
    'G', 'b' -> 6
    'T' -> 7
    'B' -> 8
    'g', 'q' -> 9
    else -> null
}
