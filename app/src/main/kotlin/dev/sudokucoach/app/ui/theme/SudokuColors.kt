package dev.sudokucoach.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de color de la app (D-12, `03-diseno.md` §5.3): monocromo con un único acento azul para
 * todo lo relacionado con la pista. Paleta fija, sin Dynamic Color.
 */
@Immutable
data class SudokuColors(
    /** Fondo de pantalla. */
    val background: Color,
    /** Bottom sheet y teclado. */
    val surface: Color,
    /** Dígitos, texto y líneas gruesas de caja. */
    val ink: Color,
    /** Texto secundario y candidatos eliminados. */
    val inkMuted: Color,
    /** Líneas finas entre celdas. */
    val gridLine: Color,
    /** Colocación, contornos, celdas sospechosas y botón principal. */
    val accent: Color,
    /** Relleno de las celdas del patrón y de la celda seleccionada. */
    val accentSoft: Color,
    /** Texto sobre el acento. */
    val onAccent: Color,
) {
    companion object {
        val Light = SudokuColors(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F5),
            ink = Color(0xFF111111),
            inkMuted = Color(0xFF69696D),
            gridLine = Color(0xFFD4D4D8),
            accent = Color(0xFF1F5FD1),
            accentSoft = Color(0xFFE3ECFB),
            onAccent = Color(0xFFFFFFFF),
        )

        val Dark = SudokuColors(
            background = Color(0xFF0F0F10),
            surface = Color(0xFF1A1A1C),
            ink = Color(0xFFEDEDED),
            inkMuted = Color(0xFF9A9A9F),
            gridLine = Color(0xFF34343A),
            accent = Color(0xFF6EA0FF),
            accentSoft = Color(0xFF1C2B47),
            onAccent = Color(0xFF0F0F10),
        )
    }
}

val LocalSudokuColors = staticCompositionLocalOf { SudokuColors.Light }
