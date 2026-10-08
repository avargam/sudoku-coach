package dev.sudokucoach.solver

/**
 * Solucionador por fuerza bruta: referencia para los tests y comprobación de unicidad.
 *
 * Trabaja solo con los valores colocados e ignora la máscara de candidatos del [Grid], para que
 * una eliminación incorrecta de una técnica no pueda esconder la solución verdadera.
 */
object Backtracker {

    /** Devuelve los 81 valores de una solución, o `null` si el tablero no tiene ninguna. */
    fun solve(grid: Grid): IntArray? {
        val search = Search.from(grid.values) ?: return null
        var solution: IntArray? = null
        search.run { solution = it.copyOf(); false }
        return solution
    }

    /** Cuenta soluciones hasta [limit]; con el valor por defecto distingue entre 0, 1 y "varias". */
    fun countSolutions(grid: Grid, limit: Int = 2): Int {
        require(limit >= 1) { "El límite debe ser al menos 1" }
        val search = Search.from(grid.values) ?: return 0
        var count = 0
        search.run { count++; count < limit }
        return count
    }

    fun hasUniqueSolution(grid: Grid): Boolean = countSolutions(grid, limit = 2) == 1

    /** Estado mutable de la búsqueda: dígitos usados por fila, columna y caja como máscaras de bits. */
    private class Search(
        private val values: IntArray,
        private val rows: IntArray,
        private val cols: IntArray,
        private val boxes: IntArray,
    ) {
        /** Recorre las soluciones; [onSolution] devuelve `false` para detener la búsqueda. */
        fun run(onSolution: (IntArray) -> Boolean) {
            step(onSolution)
        }

        private fun step(onSolution: (IntArray) -> Boolean): Boolean {
            // Heurística: la celda vacía con menos opciones.
            var best = -1
            var bestOptions = 0
            var bestCount = 10
            for (cell in 0 until 81) {
                if (values[cell] != 0) continue
                val options = ALL_CANDIDATES and (rows[Units.row(cell)] or cols[Units.col(cell)] or boxes[Units.box(cell)]).inv()
                val count = Integer.bitCount(options)
                if (count < bestCount) {
                    best = cell
                    bestOptions = options
                    bestCount = count
                    if (count <= 1) break
                }
            }
            if (best == -1) return onSolution(values)
            if (bestCount == 0) return true

            var options = bestOptions
            while (options != 0) {
                val bit = options and -options
                options = options xor bit
                place(best, bit)
                val keepGoing = step(onSolution)
                remove(best, bit)
                if (!keepGoing) return false
            }
            return true
        }

        private fun place(cell: Int, bit: Int) {
            values[cell] = Integer.numberOfTrailingZeros(bit)
            rows[Units.row(cell)] = rows[Units.row(cell)] or bit
            cols[Units.col(cell)] = cols[Units.col(cell)] or bit
            boxes[Units.box(cell)] = boxes[Units.box(cell)] or bit
        }

        private fun remove(cell: Int, bit: Int) {
            values[cell] = 0
            rows[Units.row(cell)] = rows[Units.row(cell)] and bit.inv()
            cols[Units.col(cell)] = cols[Units.col(cell)] and bit.inv()
            boxes[Units.box(cell)] = boxes[Units.box(cell)] and bit.inv()
        }

        companion object {
            /** Prepara la búsqueda, o devuelve `null` si los valores ya repiten un dígito en alguna unidad. */
            fun from(initial: IntArray): Search? {
                val search = Search(IntArray(81), IntArray(9), IntArray(9), IntArray(9))
                for (cell in 0 until 81) {
                    val digit = initial[cell]
                    if (digit == 0) continue
                    val bit = 1 shl digit
                    val used = search.rows[Units.row(cell)] or search.cols[Units.col(cell)] or search.boxes[Units.box(cell)]
                    if (used and bit != 0) return null
                    search.place(cell, bit)
                }
                return search
            }
        }
    }
}
