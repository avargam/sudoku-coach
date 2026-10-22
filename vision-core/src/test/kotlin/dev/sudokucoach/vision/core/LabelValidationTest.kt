package dev.sudokucoach.vision.core

import dev.sudokucoach.solver.Backtracker
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Units
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Valida las etiquetas completas con el `solver` (tarea 2.5): un tablero real no repite dígitos en
 * ninguna fila, columna o caja y tiene solución. Así se detectan errores de transcripción.
 */
class LabelValidationTest {

    @Test
    fun `las etiquetas completas son tableros posibles`() {
        val labelled = VisionTestData.photos().filter { it.label != null }
        assumeTrue(labelled.isNotEmpty(), "Todavía no hay etiquetas completas")
        val problems = mutableListOf<String>()
        for (photo in labelled) {
            val digits = photo.label!!.digits
            for ((unit, cells) in Units.units.withIndex()) {
                val values = cells.map { digits[it] }.filter { it != 0 }
                values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { d ->
                    problems += "${photo.id}: el $d se repite en ${Units.unitName(unit)}"
                }
            }
            if (Backtracker.countSolutions(Grid.fromValues(digits), limit = 1) == 0) {
                problems += "${photo.id}: el tablero etiquetado no tiene solución"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
        println("Etiquetas válidas: ${labelled.size}; pendientes: ${VisionTestData.photos().count { it.pending }}")
    }

    @Test
    fun `una plantilla pendiente no se toma como etiqueta`() {
        val photos = VisionTestData.photos()
        assumeTrue(photos.isNotEmpty())
        assertTrue(photos.filter { it.pending }.all { it.label == null })
    }
}
