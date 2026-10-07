package dev.sudokucoach.solver

/**
 * Geometría fija del tablero. Las celdas se numeran 0..80 en orden de filas.
 * Las unidades son 27: 0..8 filas, 9..17 columnas, 18..26 cajas.
 */
object Units {
    val units: List<IntArray> = buildList {
        for (r in 0 until 9) add(IntArray(9) { c -> r * 9 + c })
        for (c in 0 until 9) add(IntArray(9) { r -> r * 9 + c })
        for (b in 0 until 9) {
            val r0 = (b / 3) * 3
            val c0 = (b % 3) * 3
            add(IntArray(9) { i -> (r0 + i / 3) * 9 + (c0 + i % 3) })
        }
    }

    /** Celdas que comparten fila, columna o caja con cada celda (sin incluirla). */
    val peers: List<IntArray> = List(81) { cell ->
        units.filter { cell in it }.flatMap { it.asList() }.filter { it != cell }.distinct().toIntArray()
    }

    fun row(cell: Int) = cell / 9
    fun col(cell: Int) = cell % 9

    /** Nombre corto de una celda para depurar, p. ej. "F3C7" (fila 3, columna 7). */
    fun cellName(cell: Int) = "F${row(cell) + 1}C${col(cell) + 1}"

    /** Nombre corto de una unidad para depurar, p. ej. "columna 1". No es texto para el usuario (D-14). */
    fun unitName(unit: Int): String = when (unit) {
        in 0..8 -> "fila ${unit + 1}"
        in 9..17 -> "columna ${unit - 8}"
        else -> "caja ${unit - 17}"
    }
}
