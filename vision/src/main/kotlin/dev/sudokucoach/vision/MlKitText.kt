package dev.sudokucoach.vision

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/** Un carácter leído por ML Kit que se interpreta como dígito, con su caja y su confianza. */
internal class DigitSymbol(val digit: Int, val box: Rect, val confidence: Float)

/**
 * Lecturas de ML Kit que corresponden a dígitos. ML Kit reconoce texto general; solo se reinterpretan
 * como 1 la I y la barra. Se probó con más equivalencias en el A57 (tarea 2.8): de las letras solo
 * la I acertó (4 lecturas), y g → 9, Z → 2 o J/i/l → 1 dieron 8 dígitos equivocados que, sin ellas,
 * quedan "sin lectura" y la app los pide al usuario. El 0 y la O no son válidos y se descartan.
 */
internal fun Text.digitSymbols(): List<DigitSymbol> =
    textBlocks.flatMap { it.lines }.flatMap { it.elements }.flatMap { it.symbols }.mapNotNull { symbol ->
        val digit = symbol.text.singleOrNull()?.let(::digitOf) ?: return@mapNotNull null
        val box = symbol.boundingBox ?: return@mapNotNull null
        DigitSymbol(digit, box, symbol.confidence.coerceIn(0f, 1f))
    }

internal fun digitOf(c: Char): Int? = when (c) {
    in '1'..'9' -> c - '0'
    'I', '|' -> 1
    else -> null
}
