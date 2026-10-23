package dev.sudokucoach.app.explain

import dev.sudokucoach.app.explain.ExplanationArg.Cell
import dev.sudokucoach.app.explain.ExplanationArg.Cells
import dev.sudokucoach.app.explain.ExplanationArg.Digit
import dev.sudokucoach.app.explain.ExplanationArg.Digits
import dev.sudokucoach.app.explain.ExplanationArg.Eliminations
import dev.sudokucoach.app.explain.ExplanationArg.House
import dev.sudokucoach.solver.ALL_CANDIDATES
import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Argumentos de las explicaciones con pasos reales del banco del oráculo (tarea 3.6). */
class StepExplainerTest {

    /**
     * El primer paso de cada técnica al resolver los puzzles del banco, en orden. Ningún puzzle del
     * banco usa un cuarteto oculto, así que ese sale del tablero sintético de `HiddenSubsetsTest`: en la
     * fila 1, los dígitos 1–4 solo caben en F1C1, F1C4, F1C7 y F1C9.
     */
    private val steps: Map<TechniqueId, Step> by lazy {
        val dir = File(requireNotNull(System.getProperty("sudokucoach.bank")) { "Falta la propiedad sudokucoach.bank" })
        val puzzles = listOf("singles", "intersections", "subsets").flatMap { group ->
            File(dir, "$group.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        }
        val found = linkedMapOf<TechniqueId, Step>()
        val engine = HintEngine()
        for (puzzle in puzzles) {
            for (step in engine.solveLogically(Grid.parse(puzzle))) found.putIfAbsent(step.technique, step)
            if (found.keys.containsAll(HintEngine.IMPLEMENTED.keys - TechniqueId.HIDDEN_QUAD)) break
        }
        val quad = listOf(0, 3, 6, 8)
        val oneToFour = 0b11110 // bits de los dígitos 1–4
        val candidates = IntArray(81) { cell -> if (cell < 9 && cell !in quad) ALL_CANDIDATES and oneToFour.inv() else ALL_CANDIDATES }
        val technique = HintEngine.IMPLEMENTED.getValue(TechniqueId.HIDDEN_QUAD)
        found.putIfAbsent(TechniqueId.HIDDEN_QUAD, requireNotNull(technique.find(Grid(IntArray(81), candidates))))
        found
    }

    private fun Step.explained() = StepExplainer.explain(this).also { assertEquals(technique, it.technique) }

    private fun inUnit(unit: Int, cells: List<Int>) = cells.all { it in Units.units[unit] }

    private fun isBox(unit: Int) = unit >= 18

    /** Las eliminaciones del argumento son exactamente las del paso. */
    private fun assertSameEliminations(step: Step, arg: ExplanationArg) {
        val byDigit = (arg as Eliminations).byDigit
        assertEquals(byDigit.map { it.first }.sorted(), byDigit.map { it.first }, "dígitos en orden")
        val flattened = byDigit.flatMap { (digit, cells) -> cells.map { Elimination(it, digit) } }
        assertEquals(step.eliminations.toSet(), flattened.toSet())
        assertEquals(step.eliminations.size, flattened.size)
    }

    @Test
    fun `el banco tiene un paso de cada tecnica implementada`() {
        assertEquals(HintEngine.IMPLEMENTED.keys, steps.keys)
    }

    @Test
    fun `cada tecnica implementada tiene explicacion`() {
        for ((technique, step) in steps) assertTrue(step.explained().args.isNotEmpty(), "$technique")
    }

    @Test
    fun `ultima celda libre`() {
        val step = steps.getValue(TechniqueId.FULL_HOUSE)
        val (cell, house, digit) = step.explained().args
        assertEquals(Cell(step.placement!!.cell), cell)
        assertTrue(inUnit((house as House).unit, listOf(step.placement!!.cell)))
        assertEquals(Digit(step.placement!!.digit), digit)
        // Es de verdad la única celda libre de la unidad: las demás tienen valor en el paso siguiente.
        assertEquals(3, step.explained().args.size)
    }

    @Test
    fun `candidato unico`() {
        val step = steps.getValue(TechniqueId.NAKED_SINGLE)
        assertEquals(listOf(Cell(step.placement!!.cell), Digit(step.placement!!.digit)), step.explained().args)
    }

    @Test
    fun `posicion unica`() {
        val step = steps.getValue(TechniqueId.HIDDEN_SINGLE)
        val (house, digit, cell) = step.explained().args
        assertTrue(inUnit((house as House).unit, listOf(step.placement!!.cell)))
        assertEquals(Digit(step.placement!!.digit), digit)
        assertEquals(Cell(step.placement!!.cell), cell)
    }

    @Test
    fun `pares y trios bloqueados comparten caja y linea`() {
        for (technique in listOf(TechniqueId.LOCKED_PAIR, TechniqueId.LOCKED_TRIPLE)) {
            val step = steps.getValue(technique)
            val args = step.explained().args
            val cells = (args[0] as Cells).cells
            val houses = listOf((args[1] as House).unit, (args[2] as House).unit)
            assertEquals(step.highlight, cells)
            assertTrue(houses.all { inUnit(it, cells) }, "$technique")
            assertEquals(1, houses.count { isBox(it) }, "$technique: una caja y una línea")
            assertEquals(Digits(step.digits.sorted()), args[3])
            assertEquals(cells.size, step.digits.size)
            assertSameEliminations(step, args[4])
        }
    }

    @Test
    fun `bloqueos caja a linea y linea a caja`() {
        for ((technique, patternInBox) in listOf(TechniqueId.LOCKED_CANDIDATES_POINTING to true, TechniqueId.LOCKED_CANDIDATES_CLAIMING to false)) {
            val step = steps.getValue(technique)
            val (base, digit, cells, cover, eliminations) = step.explained().args
            val baseUnit = (base as House).unit
            val coverUnit = (cover as House).unit
            assertEquals(patternInBox, isBox(baseUnit), "$technique: dónde está el patrón")
            assertEquals(!patternInBox, isBox(coverUnit), "$technique: dónde se elimina")
            assertTrue(inUnit(baseUnit, (cells as Cells).cells) && inUnit(coverUnit, cells.cells))
            assertEquals(Digit(step.digits.single()), digit)
            assertSameEliminations(step, eliminations)
            // Se elimina en la unidad de destino, fuera del patrón.
            assertTrue(step.eliminations.all { it.cell in Units.units[coverUnit] && it.cell !in cells.cells })
        }
    }

    @Test
    fun `subconjuntos desnudos`() {
        for ((technique, size) in listOf(TechniqueId.NAKED_PAIR to 2, TechniqueId.NAKED_TRIPLE to 3, TechniqueId.NAKED_QUAD to 4)) {
            val step = steps.getValue(technique)
            val (house, cells, digits, eliminations) = step.explained().args
            assertEquals(size, (cells as Cells).cells.size, "$technique")
            assertEquals(size, (digits as Digits).digits.size, "$technique")
            assertTrue(inUnit((house as House).unit, cells.cells), "$technique")
            assertSameEliminations(step, eliminations)
            // Se descartan los dígitos del patrón fuera de sus celdas.
            assertTrue(step.eliminations.all { it.digit in digits.digits && it.cell !in cells.cells }, "$technique")
        }
    }

    @Test
    fun `subconjuntos ocultos`() {
        for ((technique, size) in listOf(TechniqueId.HIDDEN_PAIR to 2, TechniqueId.HIDDEN_TRIPLE to 3, TechniqueId.HIDDEN_QUAD to 4)) {
            val step = steps.getValue(technique)
            val (house, digits, cells, eliminations) = step.explained().args
            assertEquals(size, (cells as Cells).cells.size, "$technique")
            assertEquals(size, (digits as Digits).digits.size, "$technique")
            assertTrue(inUnit((house as House).unit, cells.cells), "$technique")
            assertSameEliminations(step, eliminations)
            // Se descartan los demás dígitos dentro de las celdas del patrón.
            assertTrue(step.eliminations.all { it.digit !in digits.digits && it.cell in cells.cells }, "$technique")
        }
    }

    @Test
    fun `las eliminaciones se agrupan por digito`() {
        val step = Step(
            technique = TechniqueId.NAKED_PAIR,
            highlight = listOf(9, 72),
            units = listOf(9),
            digits = listOf(3, 1),
            eliminations = listOf(Elimination(36, 5), Elimination(27, 3), Elimination(63, 3)),
        )
        assertEquals(
            listOf(House(9), Cells(listOf(9, 72)), Digits(listOf(1, 3)), Eliminations(listOf(3 to listOf(27, 63), 5 to listOf(36)))),
            StepExplainer.explain(step).args,
        )
    }
}
