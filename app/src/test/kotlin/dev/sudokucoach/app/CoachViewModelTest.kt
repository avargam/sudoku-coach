package dev.sudokucoach.app

import androidx.lifecycle.SavedStateHandle
import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.scan.ScannedBoard
import dev.sudokucoach.app.review.Validation
import dev.sudokucoach.solver.Backtracker
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
import java.io.File
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
    fun `empieza en el escaner`() {
        val viewModel = CoachViewModel(PhotoScanner { error("no se lee nada") }, SavedStateHandle())
        assertEquals(listOf<Route>(Route.Scanning), viewModel.backStack.toList())
        assertEquals(ScanState.Idle, viewModel.scanState.value)
    }

    @Test
    fun `atras en la primera pantalla cierra la app`() {
        val viewModel = CoachViewModel(PhotoScanner { error("no se lee nada") }, SavedStateHandle())
        assertFalse(viewModel.back())
        assertEquals(listOf<Route>(Route.Scanning), viewModel.backStack.toList())
    }

    @Test
    fun `un tablero escaneado en vivo lleva a la revision una sola vez`() {
        val viewModel = CoachViewModel(PhotoScanner { error("no se lee nada") }, SavedStateHandle())
        viewModel.onBoardScanned(board)
        assertContentEquals(board.values, viewModel.review.value?.board?.values)
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
        // Una captura que llega tarde, con la revisión ya abierta, se ignora.
        viewModel.onBoardScanned(board)
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
    }

    @Test
    fun `una lectura correcta lleva a la revision`() = test {
        val viewModel = CoachViewModel(PhotoScanner { ScanResult.Read(board) }, SavedStateHandle())
        viewModel.onPhotoPicked("content://foto")
        assertEquals(ScanState.Reading("content://foto"), viewModel.scanState.value)
        advanceUntilIdle()
        assertEquals(ScanState.Idle, viewModel.scanState.value)
        assertContentEquals(board.values, viewModel.review.value?.board?.values)
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
        // Atrás vuelve al escáner.
        viewModel.back()
        assertEquals(listOf<Route>(Route.Scanning), viewModel.backStack.toList())
    }

    @Test
    fun `sin tablero se queda en el escaner con el motivo`() = test {
        val viewModel = CoachViewModel(PhotoScanner { ScanResult.NoBoard }, SavedStateHandle())
        viewModel.onPhotoPicked("content://foto")
        advanceUntilIdle()
        assertEquals(ScanState.Failed(ScanFailure.NO_BOARD), viewModel.scanState.value)
        assertNull(viewModel.review.value)
        assertEquals(listOf<Route>(Route.Scanning), viewModel.backStack.toList())
    }

    @Test
    fun `una foto que no se abre da un error distinto`() = test {
        val viewModel = CoachViewModel(PhotoScanner { ScanResult.Failed("formato no soportado") }, SavedStateHandle())
        viewModel.onPhotoPicked("content://foto")
        advanceUntilIdle()
        assertEquals(ScanState.Failed(ScanFailure.UNREADABLE), viewModel.scanState.value)
    }

    @Test
    fun `mientras lee ignora otras fotos`() = test {
        val pending = CompletableDeferred<ScanResult>()
        val photos = mutableListOf<String>()
        val viewModel = CoachViewModel(PhotoScanner { photos += it; pending.await() }, SavedStateHandle())
        viewModel.onPhotoPicked("content://primera")
        advanceUntilIdle()
        viewModel.onPhotoPicked("content://segunda")
        pending.complete(ScanResult.Read(board))
        advanceUntilIdle()
        assertEquals(listOf("content://primera"), photos)
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
    }

    @Test
    fun `tras un fallo se puede elegir otra foto`() = test {
        var result: ScanResult = ScanResult.NoBoard
        val viewModel = CoachViewModel(PhotoScanner { result }, SavedStateHandle())
        viewModel.onPhotoPicked("content://mala")
        advanceUntilIdle()
        result = ScanResult.Read(board)
        viewModel.onPhotoPicked("content://buena")
        advanceUntilIdle()
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
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
        val viewModel = CoachViewModel(PhotoScanner { ScanResult.Read(scanned) }, SavedStateHandle())
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
        assertEquals(listOf(Route.Scanning, Route.Reviewing), viewModel.backStack.toList())
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
        assertEquals(listOf(Route.Scanning, Route.Reviewing, Route.Hint), viewModel.backStack.toList())
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

    @Test
    fun `tras cerrarse el proceso sigue en la misma pantalla con el mismo estado`() = test {
        val saved = SavedStateHandle()
        val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        val values = IntArray(81) { if (it % 2 == 0) solution[it].digitToInt() else 0 }
        val half = ScannedBoard(
            values = values,
            origins = List(81) { if (values[it] != 0) DigitOrigin.GIVEN else DigitOrigin.UNKNOWN },
            confidences = FloatArray(81) { 1f },
            inked = BooleanArray(81) { values[it] != 0 },
            millis = 300,
        )
        val first = CoachViewModel(PhotoScanner { ScanResult.Read(half) }, saved)
        first.onPhotoPicked("content://foto")
        advanceUntilIdle()
        first.selectCell(1)
        first.enterDigit(3) // correcto según la solución
        first.selectCell(5)
        first.showHint()
        first.revealMore()
        first.applyStep()

        // Un ViewModel nuevo con el mismo SavedStateHandle es lo que crea Android al volver.
        val restored = CoachViewModel(PhotoScanner { error("no se vuelve a leer") }, saved)
        assertEquals(first.backStack.toList(), restored.backStack.toList())
        assertContentEquals(first.review.value!!.board.values, restored.review.value!!.board.values)
        assertEquals(first.review.value!!.board.origins, restored.review.value!!.board.origins)
        assertEquals(5, restored.review.value!!.selected)
        assertEquals(first.review.value!!.validation, restored.review.value!!.validation)
        assertEquals(first.hint.value, restored.hint.value)

        // Atrás desde la pista restaurada vuelve a la revisión, con la corrección.
        restored.back()
        assertEquals(listOf(Route.Scanning, Route.Reviewing), restored.backStack.toList())
        assertEquals(3, CoachViewModel(PhotoScanner { error("") }, saved).review.value!!.board.values[1])
    }

    @Test
    fun `sin estado guardado o con uno ilegible empieza en el escaner`() {
        val saved = SavedStateHandle(mapOf("routes" to "picking,reviewing,hint", "review" to "basura"))
        val viewModel = CoachViewModel(PhotoScanner { error("") }, saved)
        assertEquals(listOf<Route>(Route.Scanning), viewModel.backStack.toList())
        assertNull(viewModel.review.value)
    }

    @Test
    fun `con siguiente se resuelve el tablero leido`() = test {
        // Un puzzle del banco del oráculo, con solución única (la media solución de arriba tiene varias).
        val bank = File(requireNotNull(System.getProperty("sudokucoach.bank")), "intersections.txt")
        val puzzle = bank.readLines().first { it.isNotBlank() && !it.startsWith("#") }.trim()
        val values = Grid.parse(puzzle).values
        val scanned = ScannedBoard(values, List(81) { DigitOrigin.GIVEN }, FloatArray(81) { 1f }, BooleanArray(81) { values[it] != 0 }, 300)
        val viewModel = reviewing(scanned)
        assertEquals(Validation.Ok, viewModel.review.value!!.validation)
        viewModel.showHint()
        var steps = 0
        while (viewModel.hint.value!!.step != null && steps++ < 200) viewModel.nextHint()
        assertTrue(viewModel.hint.value!!.solved)
        assertContentEquals(Backtracker.solve(Grid.parse(puzzle)), viewModel.hint.value!!.grid.values)
    }
}
