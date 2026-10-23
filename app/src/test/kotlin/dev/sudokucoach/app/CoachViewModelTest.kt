package dev.sudokucoach.app

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.app.scan.PhotoScanner
import dev.sudokucoach.app.scan.ScanResult
import dev.sudokucoach.app.scan.ScannedBoard
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

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
        assertEquals(ScanState.Reading, viewModel.scanState.value)
        advanceUntilIdle()
        assertEquals(ScanState.Idle, viewModel.scanState.value)
        assertSame(board, viewModel.scanned)
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
        assertNull(viewModel.scanned)
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
}
