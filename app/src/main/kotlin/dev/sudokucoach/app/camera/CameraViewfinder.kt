package dev.sudokucoach.app.camera

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation

/**
 * Visor de la cámara trasera con CameraX (D-20), recortado para llenar [modifier]. La cámara se
 * enlaza al ciclo de vida de la pantalla: se suelta al salir de ella o al ir a segundo plano.
 *
 * Si no hay cámara trasera o no se puede abrir, no se dibuja nada y queda la galería.
 */
@Composable
fun CameraViewfinder(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sin cámara trasera", e)
            return@LaunchedEffect
        }
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview)
        }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

private const val TAG = "CameraViewfinder"
