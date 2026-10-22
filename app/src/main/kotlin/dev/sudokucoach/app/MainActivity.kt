package dev.sudokucoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/** Única `Activity` de la app (D-18): todo lo demás son pantallas de Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // El tema propio con los tokens de D-12 llega en la tarea 3.1.
            MaterialTheme {
                CoachApp(onExit = ::finish)
            }
        }
    }
}

@Composable
fun CoachApp(onExit: () -> Unit, viewModel: CoachViewModel = viewModel()) {
    NavDisplay(
        backStack = viewModel.backStack,
        onBack = { if (!viewModel.back()) onExit() },
        entryProvider = entryProvider {
            entry<Route.Picking> { PickingScreen() }
        },
    )
}

/** Marcador de la pantalla inicial; el selector de fotos llega en la tarea 3.3. */
@Composable
private fun PickingScreen() {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.picking_title), style = MaterialTheme.typography.titleLarge)
        }
    }
}
