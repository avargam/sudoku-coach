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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ScanFailure
import dev.sudokucoach.app.ScanState
import dev.sudokucoach.app.camera.BufferPoint
import dev.sudokucoach.app.camera.CameraViewfinder
import dev.sudokucoach.app.camera.ViewfinderMapping
import dev.sudokucoach.app.camera.squareToQuad
import dev.sudokucoach.app.scan.FrameAnalyzer
import dev.sudokucoach.app.scan.FrameRecorder
import dev.sudokucoach.app.scan.LiveFrame
import dev.sudokucoach.app.scan.LiveScanner
import dev.sudokucoach.app.scan.LiveStatus
import dev.sudokucoach.app.scan.PhotoDecoder
import dev.sudokucoach.app.scan.ScannedBoard
import dev.sudokucoach.app.ui.theme.SudokuTheme
import java.io.File
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
        CameraScanning(state, pickPhoto, onScanned = viewModel::onBoardScanned)
    } else {
        GalleryOnly(state, pickPhoto, onAllowCamera = { permission.launch(Manifest.permission.CAMERA) })
    }
}

/**
 * Visor a pantalla completa, bajo las barras del sistema, como en la maqueta (tarea 4.6): linterna
 * arriba, el contorno del tablero en el acento (D-12, tarea 4.2), una tarjeta con el estado, que se
 * anuncia en TalkBack al cambiar, y abajo la galería y el disparador manual.
 *
 * Con el tablero quieto se lee y se vota, y el tablero votado pasa a [onScanned] (tarea 4.3). Cada
 * captura se nota con una vibración y un barrido de las celdas del tablero en el acento (la opción
 * elegida en la maqueta), y la revisión se abre al terminar. El disparador manual responde ya al
 * pulsarlo: vibra, se encoge y la tarjeta dice "Capturando…".
 */
@Composable
private fun CameraScanning(state: ScanState, onPickPhoto: () -> Unit, onScanned: (ScannedBoard) -> Unit) {
    val scanner = remember { LiveScanner() }
    val live by scanner.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var capturing by remember { mutableStateOf(false) }
    // Barrido: el contorno en el momento de la captura y los milisegundos transcurridos.
    var sweepOutline by remember { mutableStateOf<List<BufferPoint>?>(null) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(scanner) {
        scanner.captures.collect { board ->
            if (!capturing) haptics.performHapticFeedback(HapticFeedbackType.Confirm) // la manual vibró al pulsar
            sweepOutline = scanner.state.value.outline
            if (sweepOutline != null) sweep.animateTo(SWEEP_MILLIS.toFloat(), tween(SWEEP_MILLIS, easing = LinearEasing))
            onScanned(board)
        }
    }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    // Solo en debug: tiempos en logcat (4.1); con la app abierta con
    // `adb shell am start -n dev.sudokucoach/.app.MainActivity --ez measure true`, también las
    // lecturas; y el botón de grabar secuencias (4.4).
    val context = LocalContext.current
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val measure = debuggable && LocalActivity.current?.intent?.getBooleanExtra(EXTRA_MEASURE, false) == true
    val recorder = remember { if (debuggable) FrameRecorder(File(context.filesDir, "sequences")) else null }
    LightStatusIcons()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraViewfinder(
            Modifier.fillMaxSize(),
            analyzer = { FrameAnalyzer(scanner, debuggable, measure, recorder) },
            torch = torch,
            onTorchAvailable = { hasTorch = it },
        ) { mapping ->
            live.outline?.let { BoardOutline(it, mapping) }
            sweepOutline?.let { CellSweep(it, mapping, sweep.value) }
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                recorder?.let { RecordButton(it, Modifier) }
                Spacer(Modifier.weight(1f))
                if (hasTorch) TorchButton(torch, onToggle = { torch = it })
            }
            Spacer(Modifier.weight(1f))
            StatusCard(live, capturing, sweepOutline != null, scanner.maxReads, (state as? ScanState.Failed)?.reason, Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(StrokeIcons.Gallery, stringResource(R.string.gallery_pick), enabled = state !is ScanState.Reading, onClick = onPickPhoto)
                Shutter(
                    enabled = live.outline != null && !capturing,
                    pressed = capturing,
                    onClick = {
                        capturing = true
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scanner.captureNow()
                    },
                )
                Spacer(Modifier.size(48.dp))
            }
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

/**
 * Iconos claros en la barra de estado mientras se ve la cámara: con el tema claro,
 * `enableEdgeToEdge` los pone oscuros y sobre el visor no se ven (tarea 4.6). Al salir se
 * devuelven como estaban.
 */
@Composable
private fun LightStatusIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = before }
    }
}

/** Tarjeta de estado: qué hacer y, leyendo, cuántas lecturas van; el motivo si falló una foto. */
@Composable
private fun StatusCard(live: LiveFrame, capturing: Boolean, captured: Boolean, maxReads: Int, failure: ScanFailure?, modifier: Modifier) {
    val (title, hint) = when {
        captured -> R.string.live_captured to R.string.live_captured_hint
        capturing -> R.string.live_capturing to null
        else -> when (live.status) {
            LiveStatus.SEARCHING -> R.string.live_searching to R.string.live_searching_hint
            LiveStatus.TOO_FAR -> R.string.live_too_far to R.string.live_too_far_hint
            LiveStatus.HOLD_STILL -> R.string.live_hold_still to R.string.live_hold_still_hint
            LiveStatus.READING -> R.string.live_reading to R.string.live_reading_hint
        }
    }
    Column(
        modifier.fillMaxWidth().background(Scrim, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(title),
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (live.status == LiveStatus.READING && !capturing && !captured) {
                Text(stringResource(R.string.live_reads, live.reads, maxReads), color = MutedOnCamera, fontSize = 14.sp)
            }
        }
        if (live.status == LiveStatus.READING && !capturing || captured) {
            val progress by animateFloatAsState(if (captured) 1f else live.reads / maxReads.toFloat(), label = "lecturas")
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(SudokuTheme.colors.accent))
            }
        }
        val text = failure?.let { failureMessage(it) } ?: hint
        if (text != null) Text(stringResource(text), color = MutedOnCamera, fontSize = 14.sp)
    }
}

/**
 * Barrido de la captura: cada celda del tablero se tiñe del acento, en diagonal de arriba abajo,
 * siguiendo la perspectiva del contorno. [elapsed] son los ms desde la captura.
 */
@Composable
private fun CellSweep(outline: List<BufferPoint>, mapping: ViewfinderMapping, elapsed: Float) {
    val tint = SudokuTheme.colors.accent
    Canvas(Modifier.fillMaxSize()) {
        val map = squareToQuad(mapping.toLocal(outline))
        for (row in 0 until 9) for (col in 0 until 9) {
            val alpha = ((elapsed - row * ROW_DELAY - col * COL_DELAY) / CELL_FADE).coerceIn(0f, 1f) * SWEEP_ALPHA
            if (alpha == 0f) continue
            val u0 = col / 9f
            val u1 = (col + 1) / 9f
            val v0 = row / 9f
            val v1 = (row + 1) / 9f
            val cell = Path().apply {
                map(u0, v0).let { moveTo(it.x, it.y) }
                map(u1, v0).let { lineTo(it.x, it.y) }
                map(u1, v1).let { lineTo(it.x, it.y) }
                map(u0, v1).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(cell, tint.copy(alpha = alpha))
        }
    }
}

/** Linterna: botón redondo que se queda marcado mientras está encendida. */
@Composable
private fun TorchButton(on: Boolean, onToggle: (Boolean) -> Unit) {
    val description = stringResource(R.string.torch)
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) Color.White else Color.Black.copy(alpha = 0.45f))
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(StrokeIcons.Torch, contentDescription = null, Modifier.size(22.dp), tint = if (on) Color.Black else Color.White)
    }
}

/** Botón cuadrado de icono sobre la cámara (la galería). */
@Composable
private fun IconTile(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(22.dp), tint = Color.White)
    }
}

/** Disparador manual: aro blanco con un círculo que se encoge al pulsarlo; atenuado sin tablero. */
@Composable
private fun Shutter(enabled: Boolean, pressed: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.capture_now)
    val scale by animateFloatAsState(if (pressed) 0.75f else 1f, tween(PRESS_MILLIS), label = "disparador")
    Box(
        Modifier.size(76.dp).alpha(if (enabled || pressed) 1f else 0.4f).clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(58.dp).scale(scale).background(Color.White, CircleShape))
    }
}

/** Grabar una secuencia de fotogramas (solo debug, tarea 4.4). */
@Composable
private fun RecordButton(recorder: FrameRecorder, modifier: Modifier) {
    val recording by recorder.recording.collectAsStateWithLifecycle()
    TextButton(
        onClick = { if (recording) recorder.stop() else recorder.start() },
        modifier.background(if (recording) Color.Red.copy(alpha = 0.8f) else Scrim, RoundedCornerShape(50)),
    ) {
        Text(if (recording) "Parar" else "Grabar", color = Color.White)
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

/** Texto secundario sobre [Scrim], como en la maqueta. */
private val MutedOnCamera = Color(0xFFC8C8CC)

/**
 * Barrido de la captura: cada fila empieza [ROW_DELAY] ms después de la anterior y cada columna
 * [COL_DELAY], y cada celda tarda [CELL_FADE] en teñirse; en total, [SWEEP_MILLIS]. La revisión
 * se abre al terminar.
 */
private const val ROW_DELAY = 30f
private const val COL_DELAY = 6f
private const val CELL_FADE = 150f
private const val SWEEP_MILLIS = (8 * ROW_DELAY + 8 * COL_DELAY + CELL_FADE).toInt()
private const val SWEEP_ALPHA = 0.38f

/** El disparador se encoge al pulsarlo. */
private const val PRESS_MILLIS = 150
