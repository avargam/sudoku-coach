package dev.sudokucoach.solver

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Comprueba que [step] es un paso válido sobre [grid] dada la [solution] conocida:
 * no contradice la solución y cambia algo en el tablero. Sirve para el test de propiedad
 * y para los tests de cada técnica.
 */
fun assertStepConsistent(grid: Grid, step: Step, solution: IntArray) {
    val context = "${step.describe()} sobre ${grid.toText()}"

    assertTrue(step.highlight.all { it in 0..80 }, "Celdas del patrón fuera de rango: $context")
    assertTrue(step.digits.all { it in 1..9 }, "Dígitos del patrón fuera de rango: $context")

    step.placement?.let { p ->
        assertTrue(grid.isEmpty(p.cell), "Coloca en una celda ya ocupada: $context")
        assertTrue(grid.hasCandidate(p.cell, p.digit), "Coloca un dígito que no es candidato: $context")
        assertEquals(solution[p.cell], p.digit, "La colocación contradice la solución: $context")
    }

    for (e in step.eliminations) {
        assertTrue(grid.isEmpty(e.cell), "Elimina en una celda ya ocupada: $context")
        assertTrue(grid.hasCandidate(e.cell, e.digit), "Elimina un candidato que ya no estaba (paso vacío): $context")
        if (solution[e.cell] == e.digit) fail("Elimina el dígito de la solución en ${Units.cellName(e.cell)}: $context")
    }

    val duplicated = step.eliminations.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    assertTrue(duplicated.isEmpty(), "Eliminaciones repetidas $duplicated: $context")
}
