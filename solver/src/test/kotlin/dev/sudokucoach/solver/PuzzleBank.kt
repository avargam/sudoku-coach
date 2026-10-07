package dev.sudokucoach.solver

/**
 * Banco de puzzles de prueba generado con `tools/oracle/generate_bank.py` (tarea 1.3).
 * Cada grupo reúne los puzzles cuya técnica más difícil pertenece a esa familia.
 */
object PuzzleBank {
    val GROUPS = listOf("singles", "intersections", "subsets")

    /** Puzzles de un grupo, en el orden del fichero; las líneas que empiezan por `#` son comentarios. */
    fun load(group: String): List<String> {
        val resource = requireNotNull(javaClass.getResource("/bank/$group.txt")) { "No existe el grupo '$group'" }
        return resource.readText().lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    fun all(): List<String> = GROUPS.flatMap { load(it) }
}
