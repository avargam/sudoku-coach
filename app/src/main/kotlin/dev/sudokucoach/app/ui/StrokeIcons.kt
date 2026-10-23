package dev.sudokucoach.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Los pocos iconos de la app, de trazo como en la maqueta (`03-diseno.md` §5.3), en vez de añadir
 * la biblioteca de iconos de Material. Se tiñen con `Icon(tint = …)`.
 */
object StrokeIcons {
    val Back = icon { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    val Close = icon { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val ChevronLeft = icon { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    val ChevronRight = icon { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    val Erase = icon {
        moveTo(21f, 5f); lineTo(9f, 5f); lineTo(3f, 12f); lineTo(9f, 19f); lineTo(21f, 19f); close()
        moveTo(17f, 9f); lineTo(12f, 15f); moveTo(12f, 9f); lineTo(17f, 15f)
    }

    private fun icon(pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathBuilder,
            )
            .build()
}
