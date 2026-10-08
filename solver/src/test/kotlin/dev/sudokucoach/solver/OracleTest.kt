package dev.sudokucoach.solver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contraste con HoDoKu (tarea 1.10). Los pasos de HoDoKu se exportan con
 * `tools/oracle/export_steps.py`; este test no necesita Python.
 *
 * Recorre el camino de HoDoKu aplicando sus pasos (para seguir sincronizado) y, en cada estado,
 * exige que el motor elija la misma técnica y que su paso sea una de las instancias que HoDoKu
 * encuentra para esa técnica (mismas colocaciones y eliminaciones).
 */
class OracleTest {

    @Serializable
    private data class OracleFile(val hodoku: String, val order: List<String>, val puzzles: List<OraclePuzzle>)

    @Serializable
    private data class OraclePuzzle(val puzzle: String, val steps: List<OracleStep>)

    @Serializable
    private data class OracleStep(val t: String, val chosen: Int, val options: List<String>)

    /** Instancia de un paso de HoDoKu: celdas del patrón y efecto. */
    private data class Instance(val cells: Set<Int>, val placements: Set<Placement>, val eliminations: Set<Elimination>)

    private val json = Json { ignoreUnknownKeys = true }
    private val engine = HintEngine()

    private fun load(group: String): OracleFile {
        val resource = requireNotNull(javaClass.getResource("/oracle/$group.json")) { "Falta oracle/$group.json" }
        return json.decodeFromString(resource.readText())
    }

    @Test
    fun `TechniqueId esta declarado en el mismo orden que HoDoKu`() {
        val hodokuOrder = load("singles").order.map { techniqueOf(it) }
        assertEquals(hodokuOrder, TechniqueId.entries.toList())
    }

    @Test
    fun `el motor coincide con HoDoKu en cada paso del banco`() {
        val failures = mutableListOf<String>()
        var steps = 0
        var samePatternCells = 0

        for (group in PuzzleBank.GROUPS) {
            for ((index, entry) in load(group).puzzles.withIndex()) {
                var grid = Grid.parse(entry.puzzle)
                for ((n, expected) in entry.steps.withIndex()) {
                    steps++
                    val options = expected.options.map(::decode)
                    val ours = engine.nextStep(grid)
                    val where = "$group #$index paso ${n + 1} (${expected.t})"
                    when {
                        ours == null -> failures += "$where: el motor no encuentra paso"
                        ours.technique != techniqueOf(expected.t) ->
                            failures += "$where: el motor elige ${ours.describe()}"
                        options.none { it.placements == ours.placement.toSet() && it.eliminations == ours.eliminations.toSet() } ->
                            failures += "$where: ${ours.describe()} no está entre las instancias de HoDoKu ${expected.options}"
                        options.any { it.cells == ours.highlight.toSet() } -> samePatternCells++
                    }
                    grid = grid.apply(toStep(options[expected.chosen], techniqueOf(expected.t)))
                }
                if (!grid.isSolved()) failures += "$group #$index: el camino de HoDoKu no deja el tablero resuelto"
            }
        }

        println("Oráculo: $steps pasos comparados; celdas del patrón iguales a HoDoKu en $samePatternCells")
        if (failures.isNotEmpty()) {
            fail("${failures.size} discrepancias con HoDoKu. Primeras:\n" + failures.take(15).joinToString("\n"))
        }
        assertTrue(steps > 0)
    }

    /** Equivalencia entre los nombres de técnica de HoDoKu y [TechniqueId]. */
    private fun techniqueOf(hodoku: String): TechniqueId = when (hodoku) {
        "LOCKED_CANDIDATES_1" -> TechniqueId.LOCKED_CANDIDATES_POINTING
        "LOCKED_CANDIDATES_2" -> TechniqueId.LOCKED_CANDIDATES_CLAIMING
        "NAKED_QUADRUPLE" -> TechniqueId.NAKED_QUAD
        "HIDDEN_QUADRUPLE" -> TechniqueId.HIDDEN_QUAD
        else -> TechniqueId.valueOf(hodoku)
    }

    /** Decodifica `celdas|colocaciones|eliminaciones`, p. ej. `9,63||27-3,54-3`. */
    private fun decode(code: String): Instance {
        val (cells, placements, eliminations) = code.split("|")
        fun pairs(text: String, separator: Char) = text.split(",").filter { it.isNotEmpty() }
            .map { it.split(separator).let { (a, b) -> a.toInt() to b.toInt() } }
        return Instance(
            cells = cells.split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toSet(),
            placements = pairs(placements, '=').map { (c, d) -> Placement(c, d) }.toSet(),
            eliminations = pairs(eliminations, '-').map { (c, d) -> Elimination(c, d) }.toSet(),
        )
    }

    /** Paso aplicable a partir de una instancia de HoDoKu (solo tiene colocaciones de una en una). */
    private fun toStep(instance: Instance, technique: TechniqueId) = Step(
        technique = technique,
        highlight = instance.cells.toList(),
        placement = instance.placements.singleOrNull(),
        eliminations = instance.eliminations.toList(),
    )

    private fun Placement?.toSet(): Set<Placement> = if (this == null) emptySet() else setOf(this)
}
