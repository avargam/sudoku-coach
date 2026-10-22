package dev.sudokucoach.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema de la app (D-12): los tokens de [SudokuColors] para lo propio (tablero, resaltados) y un
 * `ColorScheme` de Material 3 derivado de ellos para los componentes estándar. Sigue el modo del sistema.
 */
@Composable
fun SudokuCoachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) SudokuColors.Dark else SudokuColors.Light
    CompositionLocalProvider(LocalSudokuColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = SudokuTypography,
            content = content,
        )
    }
}

/** Acceso a los tokens desde cualquier composable: `SudokuTheme.colors.accent`. */
object SudokuTheme {
    val colors: SudokuColors
        @Composable @ReadOnlyComposable
        get() = LocalSudokuColors.current
}

/** Todo lo que no es acento es la escala monocroma; así ningún componente introduce otro color. */
internal fun SudokuColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkMuted,
        surfaceTint = accent,
        surfaceBright = background,
        surfaceDim = surface,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = inkMuted,
        outlineVariant = gridLine,
    )
}
