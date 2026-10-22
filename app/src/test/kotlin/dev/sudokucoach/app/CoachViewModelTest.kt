package dev.sudokucoach.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoachViewModelTest {

    @Test
    fun `empieza eligiendo la foto`() {
        assertEquals(listOf<Route>(Route.Picking), CoachViewModel().backStack.toList())
    }

    @Test
    fun `atras en la primera pantalla cierra la app`() {
        val viewModel = CoachViewModel()
        assertFalse(viewModel.back())
        assertEquals(listOf<Route>(Route.Picking), viewModel.backStack.toList())
    }
}
