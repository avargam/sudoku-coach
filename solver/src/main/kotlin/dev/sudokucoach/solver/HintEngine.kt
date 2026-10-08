package dev.sudokucoach.solver

import dev.sudokucoach.solver.techniques.FullHouse
import dev.sudokucoach.solver.techniques.HiddenPair
import dev.sudokucoach.solver.techniques.HiddenQuad
import dev.sudokucoach.solver.techniques.HiddenSingle
import dev.sudokucoach.solver.techniques.HiddenTriple
import dev.sudokucoach.solver.techniques.LockedCandidatesClaiming
import dev.sudokucoach.solver.techniques.LockedCandidatesPointing
import dev.sudokucoach.solver.techniques.LockedPair
import dev.sudokucoach.solver.techniques.LockedTriple
import dev.sudokucoach.solver.techniques.NakedPair
import dev.sudokucoach.solver.techniques.NakedQuad
import dev.sudokucoach.solver.techniques.NakedSingle
import dev.sudokucoach.solver.techniques.NakedTriple

/**
 * Recorre las técnicas en orden y devuelve el primer paso que aplique.
 * Por defecto usa el orden de declaración de [TechniqueId], que sigue a HoDoKu (D-13);
 * [withOrder] permite otro.
 */
class HintEngine(private val techniques: List<Technique> = DEFAULT_TECHNIQUES) {

    fun nextStep(grid: Grid): Step? {
        if (grid.isSolved()) return null
        return techniques.firstNotNullOfOrNull { it.find(grid) }
    }

    /**
     * Aplica pasos hasta resolver el tablero o hasta que ninguna técnica aplique.
     * Falla si una técnica devuelve un paso que no cambia el tablero, en lugar de entrar en un bucle infinito.
     */
    fun solveLogically(grid: Grid): List<Step> {
        val steps = mutableListOf<Step>()
        var current = grid
        while (true) {
            val step = nextStep(current) ?: break
            val next = current.apply(step)
            check(next != current) { "Paso sin efecto: ${step.describe()}" }
            steps += step
            current = next
        }
        return steps
    }

    companion object {
        /**
         * Técnicas implementadas, en cualquier orden: el orden lo da [TechniqueId].
         * Registrar aquí cada técnica nueva es el único paso necesario para que el motor la use.
         */
        val IMPLEMENTED: Map<TechniqueId, Technique> = listOf(
            FullHouse,
            NakedSingle,
            HiddenSingle,
            LockedCandidatesPointing,
            LockedCandidatesClaiming,
            LockedPair,
            LockedTriple,
            NakedPair,
            NakedTriple,
            NakedQuad,
            HiddenPair,
            HiddenTriple,
            HiddenQuad,
        ).also { all ->
            // associateBy descartaría en silencio una técnica registrada dos veces con el mismo id.
            check(all.map { it.id }.toSet().size == all.size) { "Hay técnicas con el mismo TechniqueId" }
        }.associateBy { it.id }

        /** Técnicas implementadas en el orden de declaración de [TechniqueId] (D-13). */
        val DEFAULT_TECHNIQUES: List<Technique> = TechniqueId.entries.mapNotNull { IMPLEMENTED[it] }

        /** Motor con un orden propio; falla si se pide una técnica que aún no está implementada. */
        fun withOrder(order: List<TechniqueId>): HintEngine =
            HintEngine(order.map { requireNotNull(IMPLEMENTED[it]) { "Técnica no implementada: $it" } })
    }
}
