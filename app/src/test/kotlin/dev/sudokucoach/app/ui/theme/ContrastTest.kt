package dev.sudokucoach.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contraste WCAG 2 de cada par de colores que se usa para texto (tarea 3.1). Exige 4,5 (nivel AA
 * para texto normal) en los dos modos, porque los candidatos de la mini-rejilla son texto pequeño.
 * Así, un cambio de paleta no puede romper la legibilidad sin que lo note un test.
 */
class ContrastTest {

    /** Texto sobre fondo, con dónde aparece cada par. */
    private val textPairs: List<Triple<String, (SudokuColors) -> Color, (SudokuColors) -> Color>> = listOf(
        Triple("dígitos sobre el fondo", { it.ink }, { it.background }),
        Triple("texto en el bottom sheet y el teclado", { it.ink }, { it.surface }),
        Triple("dígitos en celdas del patrón o seleccionadas", { it.ink }, { it.accentSoft }),
        Triple("texto secundario y candidatos eliminados", { it.inkMuted }, { it.background }),
        Triple("texto secundario en el bottom sheet", { it.inkMuted }, { it.surface }),
        Triple("candidato eliminado en una celda del patrón", { it.inkMuted }, { it.accentSoft }),
        Triple("dígito a colocar", { it.accent }, { it.background }),
        Triple("dígito a colocar en una celda del patrón", { it.accent }, { it.accentSoft }),
        Triple("enlaces y botones de texto en el bottom sheet", { it.accent }, { it.surface }),
        Triple("texto del botón principal", { it.onAccent }, { it.accent }),
    )

    @Test
    fun `todo el texto alcanza un contraste de 4,5 en claro y oscuro`() {
        val failures = buildList {
            for ((mode, colors) in listOf("claro" to SudokuColors.Light, "oscuro" to SudokuColors.Dark)) {
                for ((use, text, back) in textPairs) {
                    val ratio = contrast(text(colors), back(colors))
                    if (ratio < MIN_TEXT_CONTRAST) add("%s, %s: %.2f".format(mode, use, ratio))
                }
            }
        }
        assertTrue(failures.isEmpty(), "Contraste insuficiente:\n" + failures.joinToString("\n"))
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** Luminancia relativa de WCAG 2 a partir de los canales sRGB. */
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
