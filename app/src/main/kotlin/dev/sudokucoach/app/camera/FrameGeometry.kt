package dev.sudokucoach.app.camera

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
