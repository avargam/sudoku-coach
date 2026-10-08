package dev.sudokucoach.solver.techniques

import dev.sudokucoach.solver.Elimination
import dev.sudokucoach.solver.Grid
import dev.sudokucoach.solver.Step
import dev.sudokucoach.solver.Technique
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units

/** N celdas de una unidad que entre todas solo admiten N dígitos: esos dígitos salen del resto. */
object NakedPair : Technique by NakedSubset(TechniqueId.NAKED_PAIR, size = 2, locked = false)
object NakedTriple : Technique by NakedSubset(TechniqueId.NAKED_TRIPLE, size = 3, locked = false)
object NakedQuad : Technique by NakedSubset(TechniqueId.NAKED_QUAD, size = 4, locked = false)

/**
 * Subconjunto desnudo que además está en la intersección de una caja y una línea, con eliminaciones
 * en las dos. HoDoKu lo clasifica como intersección y lo prueba antes que Locked Candidates.
 */
object LockedPair : Technique by NakedSubset(TechniqueId.LOCKED_PAIR, size = 2, locked = true)
object LockedTriple : Technique by NakedSubset(TechniqueId.LOCKED_TRIPLE, size = 3, locked = true)

/**
 * Búsqueda común de subconjuntos desnudos de tamaño [size]. Reproduce la semántica de HoDoKu para que
 * el oráculo pueda comparar los pasos:
 * - las celdas candidatas tienen entre 1 y [size] candidatos y su unión tiene exactamente [size] dígitos;
 * - se elimina en todas las unidades que comparten las celdas, no solo en la que se encontró el patrón;
 * - el subconjunto es "locked" si comparte caja y línea y elimina tanto en la caja como en la línea
 *   fuera de la caja. Las variantes locked solo devuelven esos casos y las naked solo los demás,
 *   así un mismo patrón nunca se informa con dos técnicas.
 *
 * Se recorren primero las cajas, luego las filas y luego las columnas, como HoDoKu.
 */
private class NakedSubset(override val id: TechniqueId, private val size: Int, private val locked: Boolean) : Technique {

    override fun find(grid: Grid): Step? {
        // Un subconjunto locked comparte siempre una caja: basta con buscar en las cajas.
        val searchOrder = if (locked) BOXES else BOXES + ROWS + COLS
        for (unit in searchOrder) {
            val eligible = Units.units[unit].filter { Integer.bitCount(grid.candidates[it]) in 1..size }
            if (eligible.size < size) continue
            search(grid, eligible, start = 0, chosen = mutableListOf(), mask = 0)?.let { return it }
        }
        return null
    }

    /**
     * Recorre las combinaciones de [size] celdas descartando pronto las que ya superan [size] dígitos,
     * y devuelve el paso de la primera que sea válida para esta técnica.
     */
    private fun search(grid: Grid, eligible: List<Int>, start: Int, chosen: MutableList<Int>, mask: Int): Step? {
        if (chosen.size == size) {
            return if (Integer.bitCount(mask) == size) buildStep(grid, chosen, mask) else null
        }
        for (i in start until eligible.size) {
            val union = mask or grid.candidates[eligible[i]]
            if (Integer.bitCount(union) > size) continue
            chosen += eligible[i]
            val found = search(grid, eligible, i + 1, chosen, union)
            chosen.removeAt(chosen.lastIndex)
            if (found != null) return found
        }
        return null
    }

    /** Paso para estas celdas, o `null` si no elimina nada o si su clasificación locked/naked no es la de esta técnica. */
    private fun buildStep(grid: Grid, cells: List<Int>, digitMask: Int): Step? {
        val first = cells.first()
        val sharedUnits = listOf(Units.boxUnit(first), Units.rowUnit(first), Units.colUnit(first))
            .filter { unit -> cells.all { it in Units.units[unit] } }

        val eliminations = linkedSetOf<Elimination>()
        val unitsWithEliminations = mutableListOf<Int>()
        var eliminatesOutsideBox = false
        for (unit in sharedUnits) {
            var any = false
            for (cell in Units.units[unit]) {
                if (cell in cells) continue
                val hits = grid.candidates[cell] and digitMask
                if (hits == 0) continue
                any = true
                if (Units.box(cell) != Units.box(first)) eliminatesOutsideBox = true
                for (digit in 1..9) if (hits and (1 shl digit) != 0) eliminations += Elimination(cell, digit)
            }
            if (any) unitsWithEliminations += unit
        }
        if (eliminations.isEmpty()) return null

        val sharesBox = Units.boxUnit(first) in sharedUnits
        val sharesLine = sharedUnits.size > 1
        val eliminatesInBox = sharesBox && Units.boxUnit(first) in unitsWithEliminations
        val isLocked = size < 4 && sharesBox && sharesLine && eliminatesInBox && eliminatesOutsideBox
        if (isLocked != locked) return null

        return Step(
            technique = id,
            highlight = cells.toList(),
            units = unitsWithEliminations,
            digits = (1..9).filter { digitMask and (1 shl it) != 0 },
            eliminations = eliminations.toList(),
        )
    }

    private companion object {
        val ROWS = (0..8).toList()
        val COLS = (9..17).toList()
        val BOXES = (18..26).toList()
    }
}
