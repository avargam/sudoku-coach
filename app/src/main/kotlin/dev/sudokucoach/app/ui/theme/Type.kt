package dev.sudokucoach.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.sudokucoach.app.R

/**
 * IBM Plex Sans (OFL, licencia en `assets/licenses`), empaquetada para no depender de la red (D-03).
 * Es una fuente variable: un solo archivo para los tres pesos. Sus cifras ya son tabulares (todas
 * miden lo mismo), así que los dígitos del tablero quedan alineados sin activar `tnum`.
 */
private fun plexSans(weight: FontWeight) = Font(
    R.font.ibm_plex_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PlexSans = FontFamily(
    plexSans(FontWeight.Normal),
    plexSans(FontWeight.Medium),
    plexSans(FontWeight.Bold),
)

private fun TextStyle.plex() = copy(fontFamily = PlexSans)

/** La escala tipográfica de Material 3 con IBM Plex Sans en todos los estilos. */
val SudokuTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.plex(),
        displayMedium = displayMedium.plex(),
        displaySmall = displaySmall.plex(),
        headlineLarge = headlineLarge.plex(),
        headlineMedium = headlineMedium.plex(),
        headlineSmall = headlineSmall.plex(),
        titleLarge = titleLarge.plex(),
        titleMedium = titleMedium.plex(),
        titleSmall = titleSmall.plex(),
        bodyLarge = bodyLarge.plex(),
        bodyMedium = bodyMedium.plex(),
        bodySmall = bodySmall.plex(),
        labelLarge = labelLarge.plex(),
        labelMedium = labelMedium.plex(),
        labelSmall = labelSmall.plex(),
    )
}
