package dev.sudokucoach.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ScanFailure
import dev.sudokucoach.app.ScanState
import dev.sudokucoach.app.scan.PhotoDecoder
import dev.sudokucoach.app.ui.theme.SudokuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Elegir una foto de la galería y leerla (tarea 3.3). El selector del sistema no pide permisos.
 * Mientras se lee, se muestra la foto con un indicador de progreso (tarea 3.5).
 */
@Composable
fun PickingScreen(viewModel: CoachViewModel) {
    val state by viewModel.scanState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(uri.toString())
    }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.picking_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            when (val s = state) {
                is ScanState.Reading -> {
                    PhotoPreview(s.photo, Modifier.weight(1f, fill = false))
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scan_reading), color = SudokuTheme.colors.inkMuted)
                }
                else -> {
                    if (s is ScanState.Failed) {
                        val message = when (s.reason) {
                            ScanFailure.NO_BOARD -> R.string.scan_no_board
                            ScanFailure.UNREADABLE -> R.string.scan_unreadable
                        }
                        Text(stringResource(message), color = SudokuTheme.colors.inkMuted, textAlign = TextAlign.Center)
                    }
                    Button(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.pick_photo))
                    }
                }
            }
        }
    }
}

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
