package dev.sudokucoach.app.review

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.solver.Units
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Revisión y validación del tablero (tarea 3.4, D-19). */
class ReviewBoardTest {

    /** Una etiqueta de `testdata/vision/labels` como tablero leído sin dudas. */
    private class Label(val id: String, val board: ReviewBoard)

    private val labels: List<Label> by lazy {
        val dir = File(requireNotNull(System.getProperty("sudokucoach.labels")) { "Falta la propiedad sudokucoach.labels" })
        dir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }.map { file ->
            val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val values = lines.take(9).joinToString("").map { if (it == '.') 0 else it.digitToInt() }.toIntArray()
            val origins = lines.drop(9).take(9).joinToString("").map {
                when (it) {
                    'P' -> DigitOrigin.GIVEN
                    'M' -> DigitOrigin.USER
                    else -> DigitOrigin.UNKNOWN
                }
            }
            Label(file.nameWithoutExtension, board(values, origins))
        }
    }

    private fun board(
        values: IntArray,
        origins: List<DigitOrigin> = List(81) { if (values[it] == 0) DigitOrigin.UNKNOWN else DigitOrigin.GIVEN },
        confidences: FloatArray = FloatArray(81) { 1f },
        inked: BooleanArray = BooleanArray(81) { values[it] != 0 },
    ) = ReviewBoard(values, origins, confidences, inked)

    /** Dígito distinto del actual que no repite ningún dígito de las celdas vecinas, o `null`. */
    private fun ReviewBoard.freeAlternative(cell: Int): Int? =
        (1..9).firstOrNull { d -> d != values[cell] && Units.peers[cell].none { values[it] == d } }

    @Test
    fun `las 30 etiquetas pasan la validacion`() {
        assertEquals(30, labels.size)
        for (label in labels) {
            val validation = label.board.validate()
            assertTrue(validation.allowsHints, "${label.id}: $validation")
            assertTrue(label.board.suspicious.isEmpty(), "${label.id}: sospechosas ${label.board.suspicious}")
        }
    }

    @Test
    fun `un manuscrito cambiado se marca como posible error del usuario`() {
        var checked = 0
        for (label in labels) {
            val b = label.board
            // En un tablero lleno cualquier cambio repite un dígito; hacen falta celdas con alternativa libre.
            val cell = (0 until 81).firstOrNull { b.origins[it] == DigitOrigin.USER && b.freeAlternative(it) != null } ?: continue
            val changed = b.edit(cell, b.freeAlternative(cell)!!)
            assertEquals(Validation.UserErrors(setOf(cell)), changed.validate(), "${label.id} ${Units.cellName(cell)}")
            checked++
        }
        assertTrue(checked >= 5, "Solo $checked etiquetas con un manuscrito cambiable")
    }

    /**
     * Cada impreso de cada etiqueta cambiado por cada dígito que no crea un repetido (176 casos). Lo
     * normal es pedir revisar la lectura (112). Si el tablero sigue teniendo solución, el error no se
     * puede detectar (58); y si los impresos cambiados siguen teniendo solución única, se culpa a los
     * manuscritos (6, en 004, 015 y 019): es el riesgo que acepta D-19, porque la primera defensa es
     * la confianza de la lectura.
     */
    @Test
    fun `un impreso cambiado pide revisar la lectura`() {
        val results = labels.flatMap { label ->
            val b = label.board
            (0 until 81).filter { b.origins[it] == DigitOrigin.GIVEN }.flatMap { cell ->
                (1..9).filter { d -> d != b.values[cell] && Units.peers[cell].none { b.values[it] == d } }.map { b.edit(cell, it).validate() }
            }
        }
        val unsolvable = results.count { it == Validation.Unsolvable }
        val blamed = results.count { it is Validation.UserErrors }
        assertTrue(unsolvable >= results.size * 0.6, "Revisar en $unsolvable de ${results.size}")
        assertTrue(blamed <= results.size * 0.05, "Se culpa al usuario en $blamed de ${results.size}")
    }

    @Test
    fun `un digito cambiado en un tablero lleno marca el repetido`() {
        val full = labels.first { l -> l.board.values.all { it != 0 } }.board
        val cell = 40
        val other = (1..9).first { it != full.values[cell] }
        val changed = full.edit(cell, other)
        val validation = changed.validate()
        assertTrue(validation is Validation.Repeated && cell in validation.cells, "$validation")
        assertFalse(validation.allowsHints)
        assertTrue(cell in changed.suspicious)
    }

    @Test
    fun `con pocos digitos hay varias soluciones y se permite seguir`() {
        val values = IntArray(81).also { it[0] = 5 }
        assertEquals(Validation.MultipleSolutions, board(values).validate())
    }

    @Test
    fun `sospechosas por tinta sin lectura y por confianza baja`() {
        val values = IntArray(81).also { it[10] = 3 }
        val b = board(
            values,
            confidences = FloatArray(81) { if (it == 10) 0.3f else 1f },
            inked = BooleanArray(81) { it == 10 || it == 20 },
        )
        assertEquals(setOf(10, 20), b.suspicious)
    }

    @Test
    fun `editar confirma la celda`() {
        val values = IntArray(81).also { it[10] = 3 }
        val b = board(
            values,
            origins = List(81) { if (it == 10) DigitOrigin.USER else DigitOrigin.UNKNOWN },
            confidences = FloatArray(81) { if (it == 10) 0.3f else 1f },
            inked = BooleanArray(81) { it == 10 || it == 20 },
        )
        val corrected = b.edit(10, 8).edit(20, 0).edit(30, 4)
        assertTrue(corrected.suspicious.isEmpty())
        // Corregir conserva el origen; un dígito nuevo es UNKNOWN; borrar quita la tinta.
        assertEquals(DigitOrigin.USER, corrected.origins[10])
        assertEquals(1f, corrected.confidences[10])
        assertEquals(DigitOrigin.UNKNOWN, corrected.origins[30])
        assertFalse(corrected.inked[20])
        assertEquals(3, b.values[10], "el tablero original no cambia")
    }

    @Test
    fun `un digito nuevo de origen desconocido cuenta como impreso`() {
        // Etiqueta con un manuscrito: quitarlo y volver a ponerlo mal como UNKNOWN ya no culpa al usuario.
        val label = labels.first { l -> (0 until 81).any { l.board.origins[it] == DigitOrigin.USER && l.board.freeAlternative(it) != null } }
        val b = label.board
        val cell = (0 until 81).first { b.origins[it] == DigitOrigin.USER && b.freeAlternative(it) != null }
        val retyped = b.edit(cell, 0).edit(cell, b.freeAlternative(cell)!!)
        assertEquals(DigitOrigin.UNKNOWN, retyped.origins[cell])
        val validation = retyped.validate()
        if (validation is Validation.UserErrors && cell in validation.cells) fail("${label.id}: se culpa al usuario de un dígito UNKNOWN")
        assertFalse(validation.allowsHints, "$validation")
    }
}
