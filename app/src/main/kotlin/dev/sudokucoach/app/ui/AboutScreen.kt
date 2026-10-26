package dev.sudokucoach.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.Libs
import dev.sudokucoach.app.R
import dev.sudokucoach.app.ui.theme.SudokuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un componente de terceros con su licencia: el texto, o un enlace si no lo trae (las condiciones de Google). */
private data class Notice(val name: String, val detail: String, val licenseName: String, val text: String?, val url: String?)

/**
 * "Acerca de" (tarea P.6): versión, enlace al código y las licencias de terceros que la APK tiene que
 * llevar (Apache-2.0 de OpenCV y AndroidX, condiciones de ML Kit…). La lista la genera AboutLibraries
 * en la compilación, a partir de las dependencias (`R.raw.aboutlibraries`); IBM Plex Sans no es una
 * dependencia, así que se añade a mano con su OFL de `assets/licenses`.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = SudokuTheme.colors
    val notices by produceState<List<Notice>>(emptyList()) { value = withContext(Dispatchers.IO) { notices(context) } }
    var shown by remember { mutableStateOf<Notice?>(null) }
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.height(48.dp).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(StrokeIcons.Back, contentDescription = stringResource(R.string.about_back), tint = colors.ink)
                    }
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // El icono de la app: su primer plano sobre su fondo, con los colores del tema.
                        Box(Modifier.size(64.dp).clip(CircleShape).background(colorResource(R.color.launcher_background))) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(64.dp))
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.about_version, appVersion(context)), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                        }
                    }
                    Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)
                    LinkRow(stringResource(R.string.about_source), onClick = { open(SOURCE_URL) })
                    Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                }
                HorizontalDivider(color = colors.gridLine)
            }
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.about_third_party), style = MaterialTheme.typography.titleMedium)
                    if (notices.isNotEmpty()) {
                        Text(stringResource(R.string.about_third_party_count, notices.size), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                    }
                }
            }
            items(notices, key = { it.name + it.detail }) { notice ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { if (notice.text != null) shown = notice else notice.url?.let(open) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(notice.name, style = MaterialTheme.typography.bodyLarge)
                    Text(listOf(notice.detail, notice.licenseName).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
        }
    }

    shown?.let { notice ->
        AlertDialog(
            onDismissRequest = { shown = null },
            confirmButton = { TextButton(onClick = { shown = null }) { Text(stringResource(R.string.about_close)) } },
            title = { Text(notice.licenseName) },
            text = { Text(notice.text.orEmpty(), Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        )
    }
}

/** Fila con un enlace que abre el navegador. */
@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    val colors = SudokuTheme.colors
    Row(Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
        Icon(StrokeIcons.External, contentDescription = null, Modifier.padding(start = 6.dp).size(18.dp), tint = colors.accent)
    }
}

private fun appVersion(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

/** Las licencias de terceros: IBM Plex Sans y las dependencias, por orden alfabético. */
private fun notices(context: Context): List<Notice> {
    val font = Notice(
        name = "IBM Plex Sans",
        detail = "IBM",
        licenseName = "SIL Open Font License 1.1",
        text = context.assets.open("licenses/ibm-plex-sans-OFL.txt").bufferedReader().use { it.readText() },
        url = null,
    )
    val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    val libraries = Libs.Builder().withJson(json).build().libraries.map { library ->
        val license = library.licenses.firstOrNull()
        Notice(
            name = library.name,
            detail = library.artifactVersion.orEmpty(),
            licenseName = library.licenses.joinToString(", ") { it.name },
            text = library.licenses.mapNotNull { it.licenseContent?.takeIf(String::isNotBlank) }.joinToString("\n\n").ifEmpty { null },
            url = license?.url ?: library.website,
        )
    }
    return (libraries + font).sortedBy { it.name.lowercase() }
}

private const val SOURCE_URL = "https://github.com/avargam/proyecto-sudoku"
