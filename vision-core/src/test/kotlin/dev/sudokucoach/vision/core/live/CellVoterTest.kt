package dev.sudokucoach.vision.core.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Votación por celda (tareas 4.3, 4.5 y 4.8). Solo cambia la celda 0; el resto del tablero está vacío.
 * Salvo que se diga, con la mejor confianza y sin celdas dudosas, para probar cada regla por separado.
 */
class CellVoterTest {
    private fun read(digit: Int, confidence: Float, inked: Boolean = true, printed: Boolean = false): List<CellReading> =
        listOf(CellReading(digit, confidence, inked, printed)) + List(80) { CellReading.EMPTY }

    private fun vote(
        vararg reads: List<CellReading>,
        voter: CellVoter = CellVoter(combine = CellVoter.Combine.BEST, faintInkShare = 1.0),
    ): CellReading {
        reads.forEach(voter::add)
        return voter.result()[0]
    }

    @Test
    fun `unanime conserva el digito y la mejor confianza`() {
        val cell = vote(read(5, 0.6f), read(5, 0.9f), read(5, 0.7f))
        assertEquals(5, cell.digit)
        assertEquals(0.9f, cell.confidence, 1e-6f)
    }

    @Test
    fun `dividida gana la mayoria con menos confianza`() {
        val cell = vote(read(1, 0.9f), read(1, 0.8f), read(4, 0.9f), read(1, 0.7f))
        assertEquals(1, cell.digit)
        assertEquals(0.9f * 3 / 4, cell.confidence, 1e-6f)
    }

    @Test
    fun `un error repetido con poca confianza no la gana`() {
        // El 1 manuscrito leído como 4 en todos los fotogramas, siempre por debajo de 0,5.
        val cell = vote(read(4, 0.3f), read(4, 0.4f), read(4, 0.35f), read(4, 0.3f), read(4, 0.4f))
        assertEquals(4, cell.digit)
        assertTrue(cell.confidence < 0.5f)
    }

    @Test
    fun `las lecturas sin digito cuentan como discrepancia`() {
        val cell = vote(read(7, 0.8f), read(0, 0f), read(0, 0f))
        assertEquals(7, cell.digit)
        assertEquals(0.8f / 3, cell.confidence, 1e-6f)
    }

    @Test
    fun `sin ninguna lectura con digito queda sin leer`() {
        assertEquals(CellReading(0, 0f, inked = true, printed = false), vote(read(0, 0f), read(0, 0f)))
    }

    @Test
    fun `la tinta y el origen se votan`() {
        // Tinta en 2 de 3: celda con tinta; impresa en 2 de esas 2.
        val inked = vote(read(3, 0.9f, printed = true), read(0, 1f, inked = false), read(3, 0.9f, printed = true))
        assertTrue(inked.inked)
        assertTrue(inked.printed)
        // Tinta en 1 de 3: vacía.
        assertEquals(CellReading.EMPTY, vote(read(3, 0.9f), read(0, 1f, inked = false), read(0, 1f, inked = false)))
        // Impresa en 1 de 3 lecturas con tinta: manuscrita.
        assertFalse(vote(read(3, 0.9f, printed = true), read(3, 0.9f), read(3, 0.9f)).printed)
    }

    @Test
    fun `con la media una lectura suelta muy segura no tapa a las demas`() {
        val cell = vote(read(4, 0.57f), read(4, 0.32f), read(4, 0.42f), read(4, 0.41f), voter = CellVoter(combine = CellVoter.Combine.MEAN))
        assertEquals(4, cell.digit)
        assertEquals((0.57f + 0.32f + 0.42f + 0.41f) / 4, cell.confidence, 1e-6f)
    }

    @Test
    fun `tinta en una parte de las lecturas deja la celda dudosa`() {
        val voter = CellVoter(faintInkShare = 0.2)
        // Un 7 tenue que el segmentador ve en 1 de 4 lecturas: con tinta, sin confianza, con su dígito.
        val cell = vote(read(7, 0.6f), read(0, 1f, inked = false), read(0, 1f, inked = false), read(0, 1f, inked = false), voter = voter)
        assertEquals(CellReading(7, 0f, inked = true, printed = false), cell)
        // Por debajo de la fracción mínima, vacía.
        val few = CellVoter(faintInkShare = 0.3)
        assertEquals(CellReading.EMPTY, vote(read(7, 0.6f), read(0, 1f, inked = false), read(0, 1f, inked = false), read(0, 1f, inked = false), voter = few))
    }

    @Test
    fun `los fotogramas solo segmentados y los trazos tenues cuentan para la celda dudosa`() {
        // Ninguna lectura ve el 7 como dígito; un trazo tenue en una lectura y tinta en 1 de 4
        // fotogramas solo segmentados: 2 de 8 observaciones, la celda queda dudosa y sin dígito.
        val voter = CellVoter(faintInkShare = 0.2)
        val empty = List(81) { false }
        val marked = listOf(true) + List(80) { false }
        voter.addInk(empty)
        voter.addInk(marked)
        voter.addInk(empty)
        voter.addInk(empty)
        val faint = listOf(CellReading(0, 1f, inked = false, printed = false, faint = true)) + List(80) { CellReading.EMPTY }
        val cell = vote(faint, read(0, 1f, inked = false), read(0, 1f, inked = false), read(0, 1f, inked = false), voter = voter)
        assertEquals(CellReading(0, 0f, inked = true, printed = false), cell)
        // Sin los fotogramas solo segmentados, 1 de 4: también dudosa; con 1 de 8 por debajo de 0,2, vacía.
        val few = CellVoter(faintInkShare = 0.2)
        repeat(4) { few.addInk(empty) }
        assertEquals(CellReading.EMPTY, vote(faint, read(0, 1f, inked = false), read(0, 1f, inked = false), read(0, 1f, inked = false), voter = few))
    }

    @Test
    fun `al reiniciar se olvidan tambien los fotogramas solo segmentados`() {
        val voter = CellVoter(faintInkShare = 0.2)
        repeat(3) { voter.addInk(listOf(true) + List(80) { false }) }
        voter.reset()
        assertEquals(CellReading.EMPTY, vote(read(0, 1f, inked = false), read(0, 1f, inked = false), voter = voter))
    }

    @Test
    fun `en empate gana el de mas confianza sumada`() {
        assertEquals(4, vote(read(1, 0.3f), read(4, 0.9f)).digit)
    }

    @Test
    fun `completa con mayoria clara desde el minimo o al llegar al maximo`() {
        val voter = CellVoter(minReads = 3, maxReads = 5, clearShare = 0.6)
        voter.add(read(5, 0.9f))
        voter.add(read(5, 0.9f))
        assertFalse(voter.complete) // menos del mínimo
        voter.add(read(6, 0.9f))
        assertTrue(voter.complete) // 2 de 3

        val split = CellVoter(minReads = 3, maxReads = 5, clearShare = 0.6)
        listOf(5, 6, 7, 5).forEach { split.add(read(it, 0.9f)) }
        assertFalse(split.complete) // 2 de 4 no llega a 0,6
        split.add(read(8, 0.9f))
        assertTrue(split.complete) // máximo de lecturas
    }

    @Test
    fun `reset empieza de cero`() {
        val voter = CellVoter()
        repeat(5) { voter.add(read(5, 0.9f)) }
        voter.reset()
        assertEquals(0, voter.count)
        assertFalse(voter.complete)
        assertEquals(CellReading.EMPTY, voter.result()[0])
    }
}
