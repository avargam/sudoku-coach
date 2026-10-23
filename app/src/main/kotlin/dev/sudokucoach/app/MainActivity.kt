package dev.sudokucoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.sudokucoach.app.scan.VisionPhotoScanner
import dev.sudokucoach.app.ui.HintScreen
import dev.sudokucoach.app.ui.PickingScreen
import dev.sudokucoach.app.ui.ReviewingScreen
import dev.sudokucoach.app.ui.theme.SudokuCoachTheme

/** Única `Activity` de la app (D-18): todo lo demás son pantallas de Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SudokuCoachTheme {
                CoachApp(onExit = ::finish)
            }
        }
    }
}

@Composable
fun CoachApp(onExit: () -> Unit) {
    // Sin inyección de dependencias (D-18): la fábrica crea el lector real con el contexto de la app.
    val resolver = LocalContext.current.applicationContext.contentResolver
    val viewModel: CoachViewModel = viewModel(factory = viewModelFactory { initializer { CoachViewModel(VisionPhotoScanner(resolver)) } })
    NavDisplay(
        backStack = viewModel.backStack,
        onBack = { if (!viewModel.back()) onExit() },
        entryProvider = entryProvider {
            entry<Route.Picking> { PickingScreen(viewModel) }
            entry<Route.Reviewing> { ReviewingScreen(viewModel, onClose = { viewModel.back() }) }
            entry<Route.Hint> { HintScreen(viewModel) }
        },
    )
}
