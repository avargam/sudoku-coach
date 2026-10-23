package dev.sudokucoach.app.ui

import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.app.CoachViewModel
import dev.sudokucoach.app.Route
import dev.sudokucoach.app.explain.ExplanationText
import dev.sudokucoach.app.scan.VisionPhotoScanner
import dev.sudokucoach.app.ui.theme.SudokuCoachTheme
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Criterio de la tarea 3.5 con la lectura real: en la foto 016 el 9 a lápiz de F2C9 es tenue; si no
 * se lee bien, la celda sale como sospechosa. Se corrigen con el teclado las sospechosas, se llega
 * a la pista y se recorre su revelado gradual hasta la siguiente (tarea 3.7).
 */
@RunWith(AndroidJUnit4::class)
class ReviewFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun laFoto016SeCorrigeConElTecladoYLlegaALaPista() {
        val file = File(appContext.cacheDir, "016.jpg")
        testContext.assets.open("016.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
        val label = testContext.assets.open("016.txt").bufferedReader().readLines().filter { !it.startsWith("#") }
            .take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }

        val viewModel = CoachViewModel(VisionPhotoScanner(appContext.contentResolver), SavedStateHandle())
        compose.setContent {
            SudokuCoachTheme {
                when (viewModel.backStack.last()) {
                    Route.Reviewing -> ReviewingScreen(viewModel, onClose = {})
                    Route.Hint -> HintScreen(viewModel, onBack = {})
                    Route.Picking -> Unit
                }
            }
        }
        compose.runOnUiThread { viewModel.onPhotoPicked(Uri.fromFile(file).toString()) }
        compose.waitUntil(timeoutMillis = 15_000) { viewModel.review.value != null }

        val board = viewModel.review.value!!.board
        val wrong = (0 until 81).filter { board.values[it] != label[it] }
        Log.i(TAG, "sospechosas ${board.suspicious}, distintas de la etiqueta $wrong, F2C9 = ${board.values[17]}")
        assertTrue("F2C9 ni leída ni sospechosa", board.values[17] == 9 || 17 in board.suspicious)
        assertTrue("Lecturas erróneas sin marcar: ${wrong - board.suspicious}", board.suspicious.containsAll(wrong))

        // Cada sospechosa se corrige (o se confirma) tocando la celda y el dígito de la etiqueta.
        for (cell in board.suspicious.sorted()) {
            compose.onNodeWithTag("cell-$cell").performScrollTo().performClick()
            val key = if (label[cell] == 0) "Borrar" else "Escribir ${label[cell]}"
            compose.onNodeWithContentDescription(key).performScrollTo().performClick()
        }
        compose.onNodeWithTag("review_flags").assertTextContains("Nada por revisar")
        compose.onNodeWithText("Ver pista").assertIsEnabled().performClick()

        val expected = HintEngine().nextStep(Grid.fromValues(label.toIntArray()))!!
        val name = appContext.getString(ExplanationText.texts(expected.technique).name)
        compose.onNodeWithTag("hint_name").assertTextContains(name)

        // Revelado gradual: dónde, qué y aplicar el paso.
        compose.onNodeWithText("Mostrar dónde").performClick()
        compose.onNodeWithText("Mostrar qué").performClick()
        compose.onNodeWithText("Aplicar paso").performClick()
        compose.onNodeWithTag("hint_applied").assertExists()
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Mostrar dónde").assertExists()
    }

    private companion object {
        const val TAG = "ReviewFlowTest"
    }
}
