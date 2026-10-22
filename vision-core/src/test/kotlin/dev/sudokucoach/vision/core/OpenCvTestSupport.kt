package dev.sudokucoach.vision.core

import org.bytedeco.javacpp.Loader
import org.bytedeco.opencv.opencv_java

/**
 * Carga los nativos de OpenCV (Bytedeco) una sola vez para los tests en la JVM.
 * En Android lo hace el módulo `vision` con el AAR oficial; el código de producción no carga nada.
 */
object OpenCvTestSupport {
    /** Ruta de la librería nativa cargada; `lazy` garantiza que la carga ocurre una sola vez. */
    private val nativeLibrary: String by lazy { Loader.load(opencv_java::class.java) }

    fun ensureLoaded() {
        nativeLibrary
    }
}
