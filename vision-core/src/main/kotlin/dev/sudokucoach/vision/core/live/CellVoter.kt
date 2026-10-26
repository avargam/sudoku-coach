package dev.sudokucoach.vision.core.live

/**
 * Vota por celda entre las lecturas de varios fotogramas del tablero quieto (D-21, tarea 4.3).
 *
 * Para cada celda:
 * - **tinta**: si la vio al menos la mitad de las lecturas. Si no, pero al menos [faintInkShare] de
 *   las **observaciones** vieron tinta o un trazo tenue (`CellReading.faint`), la celda queda
 *   **dudosa**: con tinta, sin confianza y con el dígito más leído, si lo hay, para que se revise.
 *   Es lo que pasa con los dígitos a lápiz muy tenues, que el segmentador ve solo en algunos
 *   fotogramas (tareas 4.5 y 4.8). Las observaciones son las lecturas más los fotogramas solo
 *   segmentados ([addInk]), que salen baratos: con 4 lecturas, un dígito visto en el 11 % de los
 *   fotogramas se perdía a menudo;
 * - **dígito**: el más leído entre las lecturas con tinta que leyeron alguno (en empate, el de más
 *   confianza sumada); sin ninguno, la celda queda sin leer (0, confianza 0);
 * - **confianza combinada**: la de las lecturas que dan ese dígito ([combine]: la mejor o la media),
 *   por la fracción de lecturas con tinta que lo dan. No sube por repetirse: un error que se lee
 *   igual en todos los fotogramas con poca confianza (el 1 manuscrito leído como 4) sigue por
 *   debajo del umbral. Baja si las lecturas discrepan o si algunas no leen nada;
 * - **origen**: impreso si lo es en la mayoría de las lecturas con tinta, porque el contraste
 *   cambia con la luz de cada fotograma.
 *
 * La votación está **completa** con [maxReads] lecturas, o antes, desde [minReads], si en todas las
 * celdas con tinta el valor más leído (contando "sin leer" como un valor más) llega a [clearShare].
 */
class CellVoter(
    // Valores de la tarea 4.5, ajustados con las secuencias grabadas (`LiveTuningTest`).
    private val minReads: Int = 3,
    /** Lecturas con las que la votación está completa siempre (la barra de progreso del escáner). */
    val maxReads: Int = 4,
    private val clearShare: Double = 0.8,
    private val combine: Combine = Combine.MEAN,
    /** Fracción mínima de observaciones con tinta o trazo para una celda dudosa; por encima de 1, ninguna lo es. */
    private val faintInkShare: Double = 0.2,
) {
    /** Cómo se combinan las confianzas de las lecturas que coinciden. */
    enum class Combine {
        BEST,

        /** Una lectura suelta muy segura no tapa a las demás (en los fotogramas, un error puede llegar a 0,94). */
        MEAN,
    }

    private val reads = mutableListOf<List<CellReading>>()
    private val marks = IntArray(81)
    private var observations = 0

    val count: Int get() = reads.size

    /** Una lectura completa; cuenta también como observación de tinta. */
    fun add(cells: List<CellReading>) {
        require(cells.size == 81) { "Un tablero tiene 81 celdas" }
        reads += cells
        addInk(cells.map { it.inked || it.faint })
    }

    /** Un fotograma solo segmentado: si cada celda tiene tinta o un trazo tenue (`toInkMarks`). */
    fun addInk(marked: List<Boolean>) {
        require(marked.size == 81) { "Un tablero tiene 81 celdas" }
        observations++
        marked.forEachIndexed { cell, seen -> if (seen) marks[cell]++ }
    }

    fun reset() {
        reads.clear()
        marks.fill(0)
        observations = 0
    }

    val complete: Boolean
        get() = reads.size >= maxReads || reads.size >= minReads && (0 until 81).all { isClear(it) }

    /** Las 81 celdas votadas; sin lecturas, todas vacías. */
    fun result(): List<CellReading> = List(81) { vote(it) }

    private fun inkedReads(cell: Int) = reads.map { it[cell] }.filter { it.inked }

    private fun isClear(cell: Int): Boolean {
        val inked = inkedReads(cell)
        if (inked.size * 2 < reads.size) return true // vacía, por mayoría
        val top = inked.groupingBy { it.digit }.eachCount().values.max()
        return top >= clearShare * inked.size
    }

    private fun vote(cell: Int): CellReading {
        val inked = inkedReads(cell)
        if (reads.isEmpty()) return CellReading.EMPTY
        if (inked.size * 2 < reads.size) {
            if (marks[cell] == 0 || marks[cell] < faintInkShare * observations) return CellReading.EMPTY
            val digit = inked.filter { it.digit != 0 }.groupingBy { it.digit }.eachCount().maxByOrNull { it.value }?.key ?: 0
            return CellReading(digit, 0f, inked = true, printed = false)
        }
        val printed = inked.count { it.printed } * 2 > inked.size
        val read = inked.filter { it.digit != 0 }
        if (read.isEmpty()) return CellReading(0, 0f, inked = true, printed = printed)
        val (digit, agreeing) = read.groupBy { it.digit }
            .maxWith(compareBy<Map.Entry<Int, List<CellReading>>> { it.value.size }.thenBy { e -> e.value.sumOf { it.confidence.toDouble() } })
            .let { it.key to it.value }
        val agreed = when (combine) {
            Combine.BEST -> agreeing.maxOf { it.confidence }
            Combine.MEAN -> agreeing.map { it.confidence }.average().toFloat()
        }
        val confidence = agreed * agreeing.size / inked.size
        return CellReading(digit, confidence, inked = true, printed = printed)
    }
}
