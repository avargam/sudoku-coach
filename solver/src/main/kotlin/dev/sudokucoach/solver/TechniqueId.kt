package dev.sudokucoach.solver

/**
 * Familia pedagógica de una técnica: es lo que la interfaz muestra como "nivel".
 * No determina el orden del motor, que sigue a HoDoKu (D-13).
 */
enum class Category {
    SINGLES,
    INTERSECTIONS,
    SUBSETS,
    FISH,
    WINGS,
    UNIQUENESS,
    CHAINS;

    /** Nivel que ve el usuario, de 1 (singles) a 7 (cadenas). */
    val level: Int get() = ordinal + 1
}

/**
 * Identificador estable de cada técnica. La app elige con él la plantilla de texto
 * de la explicación (D-14). Se amplía por familias según avanza el plan.
 */
enum class TechniqueId(val category: Category) {
    FULL_HOUSE(Category.SINGLES),
    NAKED_SINGLE(Category.SINGLES),
    HIDDEN_SINGLE(Category.SINGLES),

    LOCKED_PAIR(Category.INTERSECTIONS),
    LOCKED_TRIPLE(Category.INTERSECTIONS),
    LOCKED_CANDIDATES_POINTING(Category.INTERSECTIONS),
    LOCKED_CANDIDATES_CLAIMING(Category.INTERSECTIONS),

    NAKED_PAIR(Category.SUBSETS),
    NAKED_TRIPLE(Category.SUBSETS),
    HIDDEN_PAIR(Category.SUBSETS),
    HIDDEN_TRIPLE(Category.SUBSETS),
    NAKED_QUAD(Category.SUBSETS),
    HIDDEN_QUAD(Category.SUBSETS),
}
