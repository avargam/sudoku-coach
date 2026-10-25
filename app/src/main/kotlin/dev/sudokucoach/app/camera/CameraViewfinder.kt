package dev.sudokucoach.app.camera

import android.util.Log
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.CoordinateTransformer
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * Visor de la cámara trasera con CameraX (D-20), recortado para llenar [modifier]. La cámara se
 * enlaza al ciclo de vida de la pantalla: se suelta al salir de ella o al ir a segundo plano.
 *
 * Con [analyzer], cada fotograma pasa además por un `ImageAnalysis` en un solo hilo de fondo,
 * quedándose solo con el último (los que llegan mientras se analiza uno se descartan). El
 * analizador se crea al abrir la cámara y, si es [Closeable], se cierra en ese mismo hilo al
 * soltarla, después del último fotograma.
 *
 * [overlay] se dibuja encima del visor, del mismo tamaño, con un [ViewfinderMapping] para llevar
 * puntos del búfer a la pantalla (el contorno del tablero, tarea 4.2).
 *
 * Si no hay cámara trasera o no se puede abrir, no se dibuja nada y queda la galería.
 */
@Composable
fun CameraViewfinder(
    modifier: Modifier = Modifier,
    analyzer: (() -> ImageAnalysis.Analyzer)? = null,
    overlay: @Composable (ViewfinderMapping) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val transformer = remember { MutableCoordinateTransformer() }
    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        // Visor y análisis con la misma relación de aspecto, para que vean lo mismo (el contorno de 4.2).
        val preview = Preview.Builder().setResolutionSelector(selector()).build().apply { setSurfaceProvider { surfaceRequest = it } }
        val executor = Executors.newSingleThreadExecutor()
        val frames = analyzer?.invoke()
        val analysis = frames?.let {
            ImageAnalysis.Builder()
                .setResolutionSelector(selector())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(executor, it) }
        }
        val useCases = listOfNotNull<UseCase>(preview, analysis)
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sin cámara trasera", e)
        } finally {
            provider.unbind(*useCases.toTypedArray())
            analysis?.clearAnalyzer()
            (frames as? Closeable)?.let { executor.execute { it.close() } }
            executor.shutdown()
        }
    }
    surfaceRequest?.let { request ->
        Box(modifier) {
            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize(), coordinateTransformer = transformer)
            overlay(ViewfinderMapping(request.resolution, transformer))
        }
    }
}

/**
 * Del búfer de la cámara a la pantalla. CameraX da la transformación de la pantalla al búfer del
 * visor (giro, recorte y escala del visor); aquí se invierte. El análisis tiene la misma relación
 * de aspecto que el visor, así que un punto normalizado es el mismo en los dos búferes.
 */
class ViewfinderMapping(private val resolution: Size, private val transformer: CoordinateTransformer) {
    /** Los puntos en coordenadas del visor; leerlo dentro del dibujo lo repite si cambia la transformación. */
    fun toLocal(points: List<BufferPoint>): List<Offset> {
        val inverse = Matrix(transformer.transformMatrix.values.copyOf()).apply { invert() }
        return points.map { inverse.map(Offset(it.u * resolution.width, it.v * resolution.height)) }
    }
}

/** 16:9 y ≈ 1920 × 1080 (D-20). */
private fun selector() = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
    .build()

private const val TAG = "CameraViewfinder"
