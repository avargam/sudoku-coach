package dev.sudokucoach.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ScanFailure
import dev.sudokucoach.app.ScanState
import dev.sudokucoach.app.camera.CameraViewfinder
import dev.sudokucoach.app.camera.BufferPoint
import dev.sudokucoach.app.camera.ViewfinderMapping
import dev.sudokucoach.app.scan.FrameAnalyzer
import dev.sudokucoach.app.scan.LiveScanner
import dev.sudokucoach.app.scan.LiveStatus
import dev.sudokucoach.app.scan.PhotoDecoder
import dev.sudokucoach.app.ui.theme.SudokuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Primera pantalla (tarea 4.0): el visor de la cámara a pantalla completa y un botón para elegir
 * una foto de la galería (tarea 3.3), que no pide permisos. El permiso de la cámara se pide al
 * entrar; si se deniega, queda solo la galería con un texto que lo explica (D-20).
 *
 * Mientras se lee una foto, se muestra con un indicador de progreso (tarea 3.5).
 */
@Composable
fun ScanningScreen(viewModel: CoachViewModel) {
    val context = LocalContext.current
    val state by viewModel.scanState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri.toString())
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted = it }
    // Se pide una sola vez por visita, no en cada giro; al volver de los ajustes se comprueba de nuevo.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose {}
    }

    if (granted) {
        CameraScanning(state, pickPhoto)
    } else {
        GalleryOnly(state, pickPhoto, onAllowCamera = { permission.launch(Manifest.permission.CAMERA) })
    }
}

/**
 * Visor a pantalla completa, bajo las barras del sistema, con el contorno del tablero en el acento
 * (D-12, tarea 4.2) y los controles encima. La línea de estado se anuncia en TalkBack al cambiar.
 */
@Composable
private fun CameraScanning(state: ScanState, onPickPhoto: () -> Unit) {
    val scanner = remember { LiveScanner() }
    val live by scanner.state.collectAsStateWithLifecycle()
    // Medidas de la tarea 4.1, solo en debug: tiempos siempre y, al abrir la app con
    // `adb shell am start -n dev.sudokucoach/.app.MainActivity --ez measure true`, también las lecturas.
    val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val measure = debuggable && LocalActivity.current?.intent?.getBooleanExtra(EXTRA_MEASURE, false) == true
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraViewfinder(Modifier.fillMaxSize(), analyzer = { FrameAnalyzer(scanner, logStats = debuggable, measure = measure) }) { mapping ->
            live.outline?.let { BoardOutline(it, mapping) }
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(statusMessage(live.status)),
                Modifier.background(Scrim, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            if (state is ScanState.Failed) {
                Text(
                    stringResource(failureMessage(state.reason)),
                    Modifier.fillMaxWidth().background(Scrim, RoundedCornerShape(12.dp)).padding(16.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(16.dp))
            }
            GalleryButton(onPickPhoto, enabled = state !is ScanState.Reading)
        }
        if (state is ScanState.Reading) {
            Column(
                Modifier.fillMaxSize().background(Scrim).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Reading(state, textColor = Color.White)
            }
        }
    }
}

/** Contorno del tablero detectado, sobre el visor. */
@Composable
private fun BoardOutline(corners: List<BufferPoint>, mapping: ViewfinderMapping) {
    val accent = SudokuTheme.colors.accent
    Canvas(Modifier.fillMaxSize()) {
        val points = mapping.toLocal(corners)
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, accent, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    }
}

private fun statusMessage(status: LiveStatus) = when (status) {
    LiveStatus.SEARCHING -> R.string.live_searching
    LiveStatus.TOO_FAR -> R.string.live_too_far
    LiveStatus.HOLD_STILL -> R.string.live_hold_still
}

/** Sin permiso de la cámara: la entrada de la Fase 3, con el motivo y la opción de concederlo. */
@Composable
private fun GalleryOnly(state: ScanState, onPickPhoto: () -> Unit, onAllowCamera: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.picking_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            when (state) {
                is ScanState.Reading -> Reading(state, textColor = SudokuTheme.colors.inkMuted)
                else -> {
                    val message = if (state is ScanState.Failed) failureMessage(state.reason) else R.string.camera_denied
                    Text(stringResource(message), color = SudokuTheme.colors.inkMuted, textAlign = TextAlign.Center)
                    GalleryButton(onPickPhoto, enabled = true)
                    TextButton(onClick = onAllowCamera) { Text(stringResource(R.string.camera_allow)) }
                }
            }
        }
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled) {
        Icon(StrokeIcons.Gallery, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.pick_photo))
    }
}

@Composable
private fun ColumnScope.Reading(state: ScanState.Reading, textColor: Color) {
    PhotoPreview(state.photo, Modifier.weight(1f, fill = false))
    CircularProgressIndicator()
    Text(stringResource(R.string.scan_reading), color = textColor)
}

private fun failureMessage(reason: ScanFailure) = when (reason) {
    ScanFailure.NO_BOARD -> R.string.scan_no_board
    ScanFailure.UNREADABLE -> R.string.scan_unreadable
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** La foto que se está leyendo, reducida para mostrarla; nada si no se puede abrir. */
@Composable
private fun PhotoPreview(photo: String, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.IO) {
            runCatching { PhotoDecoder.decode(resolver, Uri.parse(photo), PREVIEW_SIDE).asImageBitmap() }.getOrNull()
        }
    }
    image?.let { Image(it, contentDescription = null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit) }
}

private const val PREVIEW_SIDE = 800

private const val EXTRA_MEASURE = "measure"

/** Fondo de los textos sobre la imagen de la cámara, legible con cualquier escena y tema. */
private val Scrim = Color.Black.copy(alpha = 0.6f)
