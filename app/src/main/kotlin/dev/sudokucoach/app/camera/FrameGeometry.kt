package dev.sudokucoach.app.camera

import androidx.compose.ui.geometry.Offset

/**
 * Punto en coordenadas normalizadas (0–1) del búfer de la cámara, sin girar: las que comparten el
 * análisis y el visor, que tienen la misma relación de aspecto (D-20). El visor las lleva a la
 * pantalla con la transformación de CameraX (tarea 4.2).
 */
data class BufferPoint(val u: Float, val v: Float)

/**
 * Deshace el giro de [toGrayMat]: de un punto (x, y) de la imagen girada de [width] × [height] px
 * al búfer original. Al trabajar normalizado, no importa si la imagen se redujo.
 */
fun unrotate(x: Double, y: Double, width: Int, height: Int, rotationDegrees: Int): BufferPoint {
    val u = (x / width).toFloat()
    val v = (y / height).toFloat()
    return when (rotationDegrees) {
        90 -> BufferPoint(v, 1 - u)
        180 -> BufferPoint(1 - u, 1 - v)
        270 -> BufferPoint(1 - v, u)
        else -> BufferPoint(u, v)
    }
}

/**
 * Proyección del cuadrado unidad sobre el cuadrilátero [corners] (superior izquierda, superior
 * derecha, inferior derecha, inferior izquierda): (u, v) en 0–1 dentro del tablero → punto en
 * pantalla, con la perspectiva del papel (tarea 4.6, el barrido de celdas al capturar).
 */
fun squareToQuad(corners: List<Offset>): (Float, Float) -> Offset {
    require(corners.size == 4) { "Un tablero tiene 4 esquinas" }
    val (p0, p1, p2, p3) = corners
    val dx1 = p1.x - p2.x
    val dx2 = p3.x - p2.x
    val dx3 = p0.x - p1.x + p2.x - p3.x
    val dy1 = p1.y - p2.y
    val dy2 = p3.y - p2.y
    val dy3 = p0.y - p1.y + p2.y - p3.y
    val det = dx1 * dy2 - dx2 * dy1
    val g = if (det == 0f) 0f else (dx3 * dy2 - dx2 * dy3) / det
    val h = if (det == 0f) 0f else (dx1 * dy3 - dx3 * dy1) / det
    val a = p1.x - p0.x + g * p1.x
    val b = p3.x - p0.x + h * p3.x
    val d = p1.y - p0.y + g * p1.y
    val e = p3.y - p0.y + h * p3.y
    return { u, v ->
        val w = g * u + h * v + 1
        Offset((a * u + b * v + p0.x) / w, (d * u + e * v + p0.y) / w)
    }
}
