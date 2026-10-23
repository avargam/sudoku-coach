package dev.sudokucoach.app.explain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.HintEngine
import dev.sudokucoach.solver.Placement
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.TechniqueId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las plantillas de `strings.xml` con los argumentos de [StepExplainer] (tarea 3.6): cada marcador
 * existe y tiene el tipo correcto, y ninguno sobra ni falta. Necesita los recursos de Android, por
 * eso va en el móvil y no en la JVM.
 */
@RunWith(AndroidJUnit4::class)
class ExplanationTextTest {
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** Un paso con la forma que da cada técnica (celdas, unidades, dígitos y resultado). */
    private fun sample(technique: TechniqueId): Step = when (technique) {
        TechniqueId.FULL_HOUSE -> Step(technique, listOf(22), units = listOf(2), digits = listOf(7), placement = Placement(22, 7))
        TechniqueId.NAKED_SINGLE -> Step(technique, listOf(22), digits = listOf(7), placement = Placement(22, 7))
        TechniqueId.HIDDEN_SINGLE -> Step(technique, listOf(22), units = listOf(19), digits = listOf(7), placement = Placement(22, 7))
        TechniqueId.LOCKED_PAIR, TechniqueId.LOCKED_TRIPLE ->
            Step(technique, listOf(0, 1), units = listOf(18, 0), digits = listOf(1, 3), eliminations = listOf(Elimination(5, 1), Elimination(10, 3)))
        TechniqueId.LOCKED_CANDIDATES_POINTING ->
            Step(technique, listOf(0, 1), units = listOf(18, 0), digits = listOf(4), eliminations = listOf(Elimination(5, 4), Elimination(7, 4)))
        TechniqueId.LOCKED_CANDIDATES_CLAIMING ->
            Step(technique, listOf(0, 1), units = listOf(0, 18), digits = listOf(4), eliminations = listOf(Elimination(9, 4)))
        TechniqueId.NAKED_PAIR, TechniqueId.NAKED_TRIPLE, TechniqueId.NAKED_QUAD ->
            Step(technique, listOf(9, 72), units = listOf(9), digits = listOf(1, 3), eliminations = listOf(Elimination(27, 3), Elimination(63, 3), Elimination(36, 5)))
        TechniqueId.HIDDEN_PAIR, TechniqueId.HIDDEN_TRIPLE, TechniqueId.HIDDEN_QUAD ->
            Step(technique, listOf(29, 33), units = listOf(3), digits = listOf(2, 6), eliminations = listOf(Elimination(29, 4)))
    }

    @Test
    fun cadaPlantillaUsaTodosSusArgumentosConElTipoCorrecto() {
        val marker = Regex("""%(\d+)\$([sd])""")
        for (technique in HintEngine.IMPLEMENTED.keys) {
            val explanation = StepExplainer.explain(sample(technique))
            val texts = ExplanationText.texts(technique)
            val template = resources.getString(texts.template)
            val markers = marker.findAll(template).map { it.groupValues[1].toInt() to it.groupValues[2] }.toSet()
            assertEquals("$technique: marcadores ${markers.map { it.first }}", (1..explanation.args.size).toSet(), markers.map { it.first }.toSet())
            for ((index, type) in markers) {
                val isDigit = explanation.args[index - 1] is ExplanationArg.Digit
                assertEquals("$technique: %$index\$ debería ser ${if (isDigit) "d" else "s"}", if (isDigit) "d" else "s", type)
            }
            val text = ExplanationText.explanation(resources, explanation)
            assertFalse("$technique: $text", text.contains('%'))
            // Nivel 1 sin argumentos; nivel 2 con algunos de la explicación y del mismo tipo.
            assertFalse("$technique: intro", marker.containsMatchIn(resources.getString(texts.intro)))
            val where = marker.findAll(resources.getString(texts.where)).map { it.groupValues[1].toInt() to it.groupValues[2] }.toSet()
            assertTrue("$technique: dónde sin marcadores", where.isNotEmpty())
            for ((index, type) in where) {
                assertTrue("$technique: dónde usa %$index\$", index <= explanation.args.size)
                assertEquals("$technique: dónde %$index\$", if (explanation.args[index - 1] is ExplanationArg.Digit) "d" else "s", type)
            }
            for (level in 1..3) assertFalse(ExplanationText.stage(resources, explanation, level).contains('%'))
            assertFalse(resources.getString(texts.name).isBlank())
            assertFalse(resources.getString(texts.english).isBlank())
        }
    }

    @Test
    fun textosDeEjemplo() {
        fun text(technique: TechniqueId) = ExplanationText.explanation(resources, StepExplainer.explain(sample(technique)))
        assertEquals(
            "F3C5 es la única celda libre de la fila 3, así que lleva el dígito que falta: el 7.",
            text(TechniqueId.FULL_HOUSE),
        )
        assertEquals(
            "En la columna 1, las celdas F2C1 y F9C1 solo admiten entre las dos los dígitos 1 y 3. " +
                "Esos dígitos tienen que ir en ellas, así que se descartan el 3 en F4C1 y F8C1, y el 5 en F5C1.",
            text(TechniqueId.NAKED_PAIR),
        )
        assertEquals(
            "En la caja 1, el 4 solo puede ir en F1C1 y F1C2, que están en la fila 1. " +
                "Así que el 4 de la fila 1 tiene que estar dentro de la caja 1, y se descarta el 4 en F1C6 y F1C8.",
            text(TechniqueId.LOCKED_CANDIDATES_POINTING),
        )
        assertEquals(
            "En la fila 4, los dígitos 2 y 6 solo pueden ir en F4C3 y F4C7. Esas dos celdas quedan para ellos, así que se descarta el 4 en F4C3.",
            text(TechniqueId.HIDDEN_PAIR),
        )
        assertEquals("NIVEL 3 · SUBCONJUNTOS", ExplanationText.level(resources, TechniqueId.NAKED_PAIR.category))
    }
}
