package dev.sudokucoach.app

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.scan.ScannedBoard
import dev.sudokucoach.app.review.Validation
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val board = ScannedBoard(
        values = IntArray(81) { if (it % 10 == 0) 5 else 0 },
        origins = List(81) { if (it % 10 == 0) DigitOrigin.GIVEN else DigitOrigin.UNKNOWN },
        confidences = FloatArray(81) { 1f },
        inked = BooleanArray(81) { it % 10 == 0 },
        millis = 300,
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun `empieza eligiendo la foto`() {
        val viewModel = CoachViewModel { error("no se lee nada") }
        assertEquals(listOf<Route>(Route.Picking), viewModel.backStack.toList())
        assertEquals(ScanState.Idle, viewModel.scanState.value)
    }

    @Test
    fun `atras en la primera pantalla cierra la app`() {
        val viewModel = CoachViewModel { error("no se lee nada") }
        assertFalse(viewModel.back())
        assertEquals(listOf<Route>(Route.Picking), viewModel.backStack.toList())
    }

    @Test
    fun `una lectura correcta lleva a la revision`() = test {
        val viewModel = CoachViewModel { ScanResult.Read(board) }
        viewModel.onPhotoPicked("content://foto")
        assertEquals(ScanState.Reading("content://foto"), viewModel.scanState.value)
        advanceUntilIdle()
        assertEquals(ScanState.Idle, viewModel.scanState.value)
        assertContentEquals(board.values, viewModel.review.value?.board?.values)
        assertEquals(listOf(Route.Picking, Route.Reviewing), viewModel.backStack.toList())
        // Atrás vuelve a elegir foto.
        viewModel.back()
        assertEquals(listOf<Route>(Route.Picking), viewModel.backStack.toList())
    }

    @Test
    fun `sin tablero se queda eligiendo foto con el motivo`() = test {
        val viewModel = CoachViewModel { ScanResult.NoBoard }
        viewModel.onPhotoPicked("content://foto")
        advanceUntilIdle()
        assertEquals(ScanState.Failed(ScanFailure.NO_BOARD), viewModel.scanState.value)
        assertNull(viewModel.review.value)
        assertEquals(listOf<Route>(Route.Picking), viewModel.backStack.toList())
    }

    @Test
    fun `una foto que no se abre da un error distinto`() = test {
        val viewModel = CoachViewModel { ScanResult.Failed("formato no soportado") }
        viewModel.onPhotoPicked("content://foto")
        advanceUntilIdle()
        assertEquals(ScanState.Failed(ScanFailure.UNREADABLE), viewModel.scanState.value)
    }

    @Test
    fun `mientras lee ignora otras fotos`() = test {
        val pending = CompletableDeferred<ScanResult>()
        val photos = mutableListOf<String>()
        val viewModel = CoachViewModel(PhotoScanner { photos += it; pending.await() })
        viewModel.onPhotoPicked("content://primera")
        advanceUntilIdle()
        viewModel.onPhotoPicked("content://segunda")
        pending.complete(ScanResult.Read(board))
        advanceUntilIdle()
        assertEquals(listOf("content://primera"), photos)
        assertEquals(listOf(Route.Picking, Route.Reviewing), viewModel.backStack.toList())
    }

    @Test
    fun `tras un fallo se puede elegir otra foto`() = test {
        var result: ScanResult = ScanResult.NoBoard
        val viewModel = CoachViewModel { result }
        viewModel.onPhotoPicked("content://mala")
        advanceUntilIdle()
        result = ScanResult.Read(board)
        viewModel.onPhotoPicked("content://buena")
        advanceUntilIdle()
        assertEquals(listOf(Route.Picking, Route.Reviewing), viewModel.backStack.toList())
    }

    /** Revisión de un tablero válido con dos celdas sospechosas: F1C3 sin leer y F2C1 poco segura. */
    private val solvable = run {
        val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        val values = IntArray(81) { if (it % 3 == 0) solution[it].digitToInt() else 0 }
        values[9] = 6
        ScannedBoard(
            values = values,
            origins = List(81) { if (values[it] != 0) DigitOrigin.GIVEN else DigitOrigin.UNKNOWN },
            confidences = FloatArray(81) { if (it == 9) 0.3f else 1f },
            inked = BooleanArray(81) { values[it] != 0 || it == 2 },
            millis = 300,
        )
    }

    private suspend fun TestScope.reviewing(scanned: ScannedBoard = solvable): CoachViewModel {
        val viewModel = CoachViewModel { ScanResult.Read(scanned) }
        viewModel.onPhotoPicked("content://foto")
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `la revision empieza en la primera sospechosa y salta entre ellas`() = test {
        val viewModel = reviewing()
        assertEquals(setOf(2, 9), viewModel.review.value!!.board.suspicious)
        assertEquals(2, viewModel.review.value!!.selected)
        viewModel.moveToSuspicious(forward = true)
        assertEquals(9, viewModel.review.value!!.selected)
        viewModel.moveToSuspicious(forward = true)
        assertEquals(2, viewModel.review.value!!.selected, "da la vuelta")
        viewModel.moveToSuspicious(forward = false)
        assertEquals(9, viewModel.review.value!!.selected)
    }

    @Test
    fun `corregir con el teclado quita la sospecha y revalida`() = test {
        val viewModel = reviewing()
        viewModel.selectCell(2)
        viewModel.enterDigit(4)
        val review = viewModel.review.value!!
        assertEquals(4, review.board.values[2])
        assertEquals(setOf(9), review.board.suspicious)
        assertTrue(review.validation.allowsHints, "${review.validation}")
    }

    @Test
    fun `un repetido bloquea la pista`() = test {
        val viewModel = reviewing()
        viewModel.selectCell(1)
        viewModel.enterDigit(5) // F1C1 ya es 5
        assertTrue(viewModel.review.value!!.validation is Validation.Repeated)
        viewModel.showHint()
        assertEquals(listOf(Route.Picking, Route.Reviewing), viewModel.backStack.toList())
    }

    @Test
    fun `ver pista lleva al primer paso del motor`() = test {
        // Media solución (las celdas pares): los únicos bastan para dar una pista.
        val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        val values = IntArray(81) { if (it % 2 == 0) solution[it].digitToInt() else 0 }
        val half = ScannedBoard(
            values = values,
            origins = List(81) { if (values[it] != 0) DigitOrigin.GIVEN else DigitOrigin.UNKNOWN },
            confidences = FloatArray(81) { 1f },
            inked = BooleanArray(81) { values[it] != 0 },
            millis = 300,
        )
        val viewModel = reviewing(half)
        viewModel.showHint()
        assertEquals(listOf(Route.Picking, Route.Reviewing, Route.Hint), viewModel.backStack.toList())
        assertEquals(HintEngine().nextStep(Grid.fromValues(values)), viewModel.hint.value?.step)
        viewModel.revealMore()
        viewModel.applyStep()
        assertEquals(3, viewModel.hint.value?.level)
        assertTrue(viewModel.hint.value!!.applied)
        viewModel.nextHint()
        assertEquals(1, viewModel.hint.value?.level)
        // Atrás vuelve a la revisión, que conserva las correcciones.
        viewModel.back()
        assertEquals(Route.Reviewing, viewModel.backStack.last())
    }
}
