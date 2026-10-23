package dev.sudokucoach.app.explain

import android.content.res.Resources
import androidx.annotation.StringRes
import dev.sudokucoach.app.R
import dev.sudokucoach.solver.Category
import dev.sudokucoach.solver.TechniqueId
import dev.sudokucoach.solver.Units

/**
 * Textos de una técnica en `strings.xml`: nombre, nombre en inglés y un texto por nivel del
 * revelado (tarea 3.7): [intro] sin argumentos, [where] con algunos de la explicación y [template]
 * con todos.
 */
class TechniqueTexts(
    @StringRes val name: Int,
    @StringRes val english: Int,
    @StringRes val intro: Int,
    @StringRes val where: Int,
    @StringRes val template: Int,
)

/**
 * Pasa una [Explanation] a texto con los recursos (tarea 3.6, D-14). El `when` es exhaustivo: una
 * técnica nueva en `TechniqueId` no compila hasta que tiene sus textos.
 */
object ExplanationText {
    fun texts(technique: TechniqueId): TechniqueTexts = when (technique) {
        TechniqueId.FULL_HOUSE -> TechniqueTexts(R.string.technique_full_house, R.string.technique_full_house_en, R.string.intro_full_house, R.string.where_full_house, R.string.explain_full_house)
        TechniqueId.NAKED_SINGLE -> TechniqueTexts(R.string.technique_naked_single, R.string.technique_naked_single_en, R.string.intro_naked_single, R.string.where_naked_single, R.string.explain_naked_single)
        TechniqueId.HIDDEN_SINGLE -> TechniqueTexts(R.string.technique_hidden_single, R.string.technique_hidden_single_en, R.string.intro_hidden_single, R.string.where_hidden_single, R.string.explain_hidden_single)
        TechniqueId.LOCKED_PAIR -> TechniqueTexts(R.string.technique_locked_pair, R.string.technique_locked_pair_en, R.string.intro_locked_pair, R.string.where_locked_pair, R.string.explain_locked_pair)
        TechniqueId.LOCKED_TRIPLE -> TechniqueTexts(R.string.technique_locked_triple, R.string.technique_locked_triple_en, R.string.intro_locked_triple, R.string.where_locked_triple, R.string.explain_locked_triple)
        TechniqueId.LOCKED_CANDIDATES_POINTING -> TechniqueTexts(R.string.technique_pointing, R.string.technique_pointing_en, R.string.intro_pointing, R.string.where_pointing, R.string.explain_pointing)
        TechniqueId.LOCKED_CANDIDATES_CLAIMING -> TechniqueTexts(R.string.technique_claiming, R.string.technique_claiming_en, R.string.intro_claiming, R.string.where_claiming, R.string.explain_claiming)
        TechniqueId.NAKED_PAIR -> TechniqueTexts(R.string.technique_naked_pair, R.string.technique_naked_pair_en, R.string.intro_naked_pair, R.string.where_naked_pair, R.string.explain_naked_pair)
        TechniqueId.NAKED_TRIPLE -> TechniqueTexts(R.string.technique_naked_triple, R.string.technique_naked_triple_en, R.string.intro_naked_triple, R.string.where_naked_triple, R.string.explain_naked_triple)
        TechniqueId.NAKED_QUAD -> TechniqueTexts(R.string.technique_naked_quad, R.string.technique_naked_quad_en, R.string.intro_naked_quad, R.string.where_naked_quad, R.string.explain_naked_quad)
        TechniqueId.HIDDEN_PAIR -> TechniqueTexts(R.string.technique_hidden_pair, R.string.technique_hidden_pair_en, R.string.intro_hidden_pair, R.string.where_hidden_pair, R.string.explain_hidden_pair)
        TechniqueId.HIDDEN_TRIPLE -> TechniqueTexts(R.string.technique_hidden_triple, R.string.technique_hidden_triple_en, R.string.intro_hidden_triple, R.string.where_hidden_triple, R.string.explain_hidden_triple)
        TechniqueId.HIDDEN_QUAD -> TechniqueTexts(R.string.technique_hidden_quad, R.string.technique_hidden_quad_en, R.string.intro_hidden_quad, R.string.where_hidden_quad, R.string.explain_hidden_quad)
    }

    /** "NIVEL 3 · SUBCONJUNTOS". */
    fun level(resources: Resources, category: Category): String {
        val name = when (category) {
            Category.SINGLES -> R.string.level_singles
            Category.INTERSECTIONS -> R.string.level_intersections
            Category.SUBSETS -> R.string.level_subsets
            Category.FISH -> R.string.level_fish
            Category.WINGS -> R.string.level_wings
            Category.UNIQUENESS -> R.string.level_uniqueness
            Category.CHAINS -> R.string.level_chains
        }
        return resources.getString(R.string.level_format, category.level, resources.getString(name))
    }

    /** La explicación completa (nivel 3): la plantilla de la técnica con sus argumentos. */
    fun explanation(resources: Resources, explanation: Explanation): String = format(resources, texts(explanation.technique).template, explanation)

    /** El texto de un nivel del revelado: 1 presenta la técnica, 2 dice dónde mirar y 3 la explica. */
    fun stage(resources: Resources, explanation: Explanation, level: Int): String {
        val texts = texts(explanation.technique)
        return when (level) {
            1 -> resources.getString(texts.intro)
            2 -> format(resources, texts.where, explanation)
            else -> explanation(resources, explanation)
        }
    }

    /** Una plantilla con los argumentos de la explicación; las de nivel 2 usan solo algunos. */
    private fun format(resources: Resources, @StringRes template: Int, explanation: Explanation): String {
        val args = explanation.args.map { arg(resources, it) }.toTypedArray()
        return resources.getString(template, *args)
    }

    /** "F3C5". */
    fun cellName(resources: Resources, cell: Int) = cell(resources, cell)

    /** Los dígitos van como `Int` (`%d` en la plantilla); el resto, ya como texto (`%s`). */
    private fun arg(resources: Resources, arg: ExplanationArg): Any = when (arg) {
        is ExplanationArg.Cell -> cell(resources, arg.cell)
        is ExplanationArg.Cells -> list(resources, arg.cells.map { cell(resources, it) })
        is ExplanationArg.House -> house(resources, arg.unit)
        is ExplanationArg.Digit -> arg.digit
        is ExplanationArg.Digits -> list(resources, arg.digits.map { it.toString() })
        is ExplanationArg.Eliminations -> eliminations(resources, arg)
    }

    private fun cell(resources: Resources, cell: Int) = resources.getString(R.string.explain_cell, Units.row(cell) + 1, Units.col(cell) + 1)

    private fun house(resources: Resources, unit: Int) = when (unit) {
        in 0..8 -> resources.getString(R.string.explain_row, unit + 1)
        in 9..17 -> resources.getString(R.string.explain_column, unit - 8)
        else -> resources.getString(R.string.explain_box, unit - 17)
    }

    /** "a", "a y b", "a, b y c"; con [lastSeparator] se puede cambiar el último " y ". */
    private fun list(resources: Resources, items: List<String>, lastSeparator: String = resources.getString(R.string.explain_list_and)): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + lastSeparator + items.last()

    /**
     * "se descarta el 3 en F4C1 y F7C1"; con varios dígitos, "se descartan el 3 en F4C1, y el 5 en
     * F9C1": la coma antes del último "y" separa los grupos cuando alguno ya lleva su propio "y".
     */
    private fun eliminations(resources: Resources, arg: ExplanationArg.Eliminations): String {
        val groups = arg.byDigit.map { (digit, cells) -> resources.getString(R.string.explain_digit_in, digit, list(resources, cells.map { cell(resources, it) })) }
        val and = resources.getString(R.string.explain_list_and)
        val joined = list(resources, groups, if (arg.byDigit.any { it.second.size > 1 }) ",$and" else and)
        return resources.getQuantityString(R.plurals.explain_eliminations, groups.size, joined)
    }
}
