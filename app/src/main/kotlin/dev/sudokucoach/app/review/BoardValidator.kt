package dev.sudokucoach.app.review

import dev.sudokucoach.app.board.DigitOrigin
import dev.sudokucoach.solver.Backtracker
import dev.sudokucoach.solver.Grid

/** Resultado de validar un [ReviewBoard] antes de pedir pistas (D-19). */
sealed interface Validation {
    /** Si se pueden pedir pistas. */
    val allowsHints: Boolean

    /** Tiene una única solución. */
    data object Ok : Validation {
        override val allowsHints = true
    }

    /** Tiene varias soluciones: se avisa, pero las técnicas de la Fase 1 no suponen unicidad. */
    data object MultipleSolutions : Validation {
        override val allowsHints = true
    }

    /** Dígitos repetidos en una fila, columna o caja. */
    data class Repeated(val cells: Set<Int>) : Validation {
        override val allowsHints = false
    }

    /**
     * Los impresos tienen solución única y estos manuscritos no coinciden con ella: posibles errores
     * del usuario en el papel.
     */
    data class UserErrors(val cells: Set<Int>) : Validation {
        override val allowsHints = false
    }

    /** No hay solución y no se puede achacar a los manuscritos: la lectura está mal. */
    data object Unsolvable : Validation {
        override val allowsHints = false
    }
}

/**
 * Validación de D-19. Los dígitos de origen `UNKNOWN` (añadidos al revisar o sin clasificar) cuentan
 * como impresos: no se puede culpar al usuario de lo que no se sabe si escribió él.
 */
object BoardValidator {
    fun validate(board: ReviewBoard): Validation {
        if (board.repeated.isNotEmpty()) return Validation.Repeated(board.repeated)

        when (Backtracker.countSolutions(Grid.fromValues(board.values), limit = 2)) {
            1 -> return Validation.Ok
            2 -> return Validation.MultipleSolutions
        }

        val givens = IntArray(81) { if (board.origins[it] == DigitOrigin.USER) 0 else board.values[it] }
        val givensGrid = Grid.fromValues(givens)
        if (!Backtracker.hasUniqueSolution(givensGrid)) return Validation.Unsolvable
        val solution = requireNotNull(Backtracker.solve(givensGrid))
        // No puede quedar vacío: si todos los manuscritos coincidieran, el tablero completo tendría solución.
        val wrong = (0 until 81).filter { board.origins[it] == DigitOrigin.USER && board.values[it] != 0 && board.values[it] != solution[it] }
        return Validation.UserErrors(wrong.toSet())
    }
}
