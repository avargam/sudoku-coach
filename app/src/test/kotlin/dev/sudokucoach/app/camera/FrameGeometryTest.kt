package dev.sudokucoach.app.camera

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deshacer el giro de `toGrayMat` (tarea 4.2). Búfer apaisado de 1920 × 1080 que la cámara del
 * móvil en vertical entrega con `rotationDegrees` = 90: el píxel (0, 0) del búfer acaba en la
 * esquina superior derecha de la imagen girada (`Core.ROTATE_90_CLOCKWISE`).
 */
class FrameGeometryTest {
    @Test
    fun `sin giro solo normaliza`() {
        assertEquals(BufferPoint(0.5f, 0.25f), unrotate(960.0, 270.0, 1920, 1080, 0))
    }

    @Test
    fun `con 90 grados la esquina superior derecha de la imagen es el origen del bufer`() {
        // La imagen girada mide 1080 × 1920.
        assertEquals(BufferPoint(0f, 0f), unrotate(1080.0, 0.0, 1080, 1920, 90))
        assertEquals(BufferPoint(1f, 1f), unrotate(0.0, 1920.0, 1080, 1920, 90))
        // Un punto arriba a la izquierda de la imagen está al principio de la última fila del búfer.
        assertEquals(BufferPoint(0f, 1f), unrotate(0.0, 0.0, 1080, 1920, 90))
    }

    @Test
    fun `con 180 y 270 grados`() {
        assertEquals(BufferPoint(0.75f, 0.5f), unrotate(480.0, 540.0, 1920, 1080, 180))
        // ROTATE_90_COUNTERCLOCKWISE: el origen del búfer acaba abajo a la izquierda.
        assertEquals(BufferPoint(0f, 0f), unrotate(0.0, 1920.0, 1080, 1920, 270))
    }

    @Test
    fun `la escala de la imagen no cambia el resultado`() {
        assertEquals(unrotate(540.0, 480.0, 1080, 1920, 90), unrotate(450.0, 400.0, 900, 1600, 90))
    }

    @Test
    fun `el cuadrado unidad cae en las esquinas y, con perspectiva, el centro en el cruce de las diagonales`() {
        // Trapecio: más estrecho arriba, como un papel inclinado hacia atrás.
        val corners = listOf(Offset(30f, 0f), Offset(70f, 0f), Offset(100f, 100f), Offset(0f, 100f))
        val map = squareToQuad(corners)
        listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).zip(corners).forEach { (uv, corner) ->
            val p = map(uv.first, uv.second)
            assertEquals(corner.x, p.x, 1e-3f)
            assertEquals(corner.y, p.y, 1e-3f)
        }
        // Las diagonales del trapecio se cruzan en x = 50, a 40/140 de la altura.
        val center = map(0.5f, 0.5f)
        assertEquals(50f, center.x, 1e-3f)
        assertEquals(100f * 40f / 140f, center.y, 1e-2f)
    }
}
