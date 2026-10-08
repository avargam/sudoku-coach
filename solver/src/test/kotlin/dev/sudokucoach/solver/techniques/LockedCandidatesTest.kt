package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.cell
import dev.sudokucoach.solver.eliminated
import dev.sudokucoach.solver.openGrid
import dev.sudokucoach.solver.without
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LockedCandidatesTest {

    private val box1Rows2and3 = listOf(cell(1, 0), cell(1, 1), cell(1, 2), cell(2, 0), cell(2, 1), cell(2, 2))
    private val box1Cols2and3 = listOf(cell(0, 1), cell(0, 2), cell(1, 1), cell(1, 2), cell(2, 1), cell(2, 2))

    @Test
    fun `pointing en fila elimina del resto de la fila`() {
        // En la caja 1, el 5 solo queda en la fila 1.
        val grid = openGrid().without(5, box1Rows2and3)
        val step = assertNotNull(LockedCandidatesPointing.find(grid))

        assertEquals(TechniqueId.LOCKED_CANDIDATES_POINTING, step.technique)
        assertEquals(listOf(5), step.digits)
        assertEquals(listOf(cell(0, 0), cell(0, 1), cell(0, 2)), step.highlight)
        assertEquals(listOf(18, 0), step.units) // caja 1, fila 1
        assertEquals((3..8).map { cell(0, it) to 5 }.toSet(), step.eliminated())
    }

    @Test
    fun `pointing en columna elimina del resto de la columna`() {
        // En la caja 1, el 7 solo queda en la columna 1.
        val grid = openGrid().without(7, box1Cols2and3)
        val step = assertNotNull(LockedCandidatesPointing.find(grid))

        assertEquals(listOf(18, 9), step.units) // caja 1, columna 1
        assertEquals((3..8).map { cell(it, 0) to 7 }.toSet(), step.eliminated())
    }

    @Test
    fun `claiming en fila elimina del resto de la caja`() {
        // En la fila 1, el 5 solo queda dentro de la caja 1.
        val grid = openGrid().without(5, (3..8).map { cell(0, it) })
        assertNull(LockedCandidatesPointing.find(grid))
        val step = assertNotNull(LockedCandidatesClaiming.find(grid))

        assertEquals(TechniqueId.LOCKED_CANDIDATES_CLAIMING, step.technique)
        assertEquals(listOf(0, 18), step.units) // fila 1, caja 1
        assertEquals(listOf(cell(0, 0), cell(0, 1), cell(0, 2)), step.highlight)
        assertEquals(box1Rows2and3.map { it to 5 }.toSet(), step.eliminated())
    }

    @Test
    fun `claiming en columna elimina del resto de la caja`() {
        // En la columna 1, el 5 solo queda dentro de la caja 1.
        val grid = openGrid().without(5, (3..8).map { cell(it, 0) })
        val step = assertNotNull(LockedCandidatesClaiming.find(grid))

        assertEquals(listOf(9, 18), step.units) // columna 1, caja 1
        assertEquals(box1Cols2and3.map { it to 5 }.toSet(), step.eliminated())
    }

    @Test
    fun `sin eliminaciones posibles no devuelve paso`() {
        // El 5 está confinado en la fila 1 de la caja 1, pero ya no queda en ninguna otra celda de la fila ni de la caja.
        val grid = openGrid().without(5, box1Rows2and3 + (3..8).map { cell(0, it) })
        assertNull(LockedCandidatesPointing.find(grid))
        assertNull(LockedCandidatesClaiming.find(grid))
    }

    @Test
    fun `no confunde un hidden single con una interseccion`() {
        // En la caja 1 el 5 solo queda en F1C1: es un Hidden Single, no un Locked Candidates.
        val grid = openGrid().without(5, box1Rows2and3 + listOf(cell(0, 1), cell(0, 2)))
        assertNull(LockedCandidatesPointing.find(grid))
    }
}
