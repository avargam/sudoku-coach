package dev.sudokucoach.solver

/** Tablero sin valores en el que todas las celdas admiten los 9 dígitos. */
fun openGrid(): Grid = Grid(IntArray(81), IntArray(81) { ALL_CANDIDATES })

/** Copia del tablero sin el candidato [digit] en [cells]; sirve para aislar un patrón en los tests. */
fun Grid.without(digit: Int, cells: Iterable<Int>): Grid {
    val c = candidates.copyOf()
    for (cell in cells) c[cell] = c[cell] and (1 shl digit).inv()
    return Grid(values.copyOf(), c)
}

/** Celdas de la fila [r] (0..8) y columna [c] (0..8), útil para escribir los tests en coordenadas. */
fun cell(r: Int, c: Int) = r * 9 + c

/** Candidatos eliminados por un paso, como pares (celda, dígito) para compararlos sin depender del orden. */
fun Step.eliminated(): Set<Pair<Int, Int>> = eliminations.map { it.cell to it.digit }.toSet()
