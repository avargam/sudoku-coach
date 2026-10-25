package dev.sudokucoach.app.camera

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
}
