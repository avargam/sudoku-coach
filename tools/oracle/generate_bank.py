"""Genera el banco de puzzles de prueba del solver con hodoku-py (tarea 1.3).

Cada puzzle se genera con su propia semilla (semilla base + índice), de modo que el resultado
es reproducible e independiente del número de procesos. Se conservan los puzzles que HoDoKu
resuelve usando solo las técnicas de la Fase 1, y se agrupan por la familia más difícil que
necesitan:

    singles.txt        solo Full House, Naked Single y Hidden Single
    intersections.txt  además Locked Pair/Triple o Locked Candidates
    subsets.txt        además algún Naked/Hidden Pair, Triple o Quad

Uso:
    python generate_bank.py [--per-group 100] [--seed 2026] [--max-attempts 40000]
"""

from __future__ import annotations

import argparse
import multiprocessing
import random
from collections import Counter
from importlib.metadata import version
from pathlib import Path

from hodoku import Solver, SolverConfig
from hodoku import SolutionType as T
from hodoku.config import DEFAULT_CONFIG
from hodoku.generator.generator import SudokuGenerator

SINGLES = {T.FULL_HOUSE, T.NAKED_SINGLE, T.HIDDEN_SINGLE}
INTERSECTIONS = {T.LOCKED_PAIR, T.LOCKED_TRIPLE, T.LOCKED_CANDIDATES_1, T.LOCKED_CANDIDATES_2}
SUBSETS = {
    T.NAKED_PAIR, T.NAKED_TRIPLE, T.HIDDEN_PAIR,
    T.HIDDEN_TRIPLE, T.NAKED_QUADRUPLE, T.HIDDEN_QUADRUPLE,
}
PHASE_1 = SINGLES | INTERSECTIONS | SUBSETS
GROUPS = ("singles", "intersections", "subsets")

# Solo las técnicas de la Fase 1, en el orden por defecto de HoDoKu.
CONFIG = SolverConfig(step_overrides={
    s.solution_type: {"enabled": False}
    for s in DEFAULT_CONFIG.solver_steps
    if s.solution_type not in PHASE_1
})

OUTPUT_DIR = Path(__file__).resolve().parents[2] / "solver/src/test/resources/bank"


def classify(index_and_seed: tuple[int, int]) -> tuple[int, str | None, str, list[str]]:
    """Genera el puzzle `index` y devuelve su grupo (o None si no se resuelve) y sus técnicas."""
    index, base_seed = index_and_seed
    generator = SudokuGenerator(rng=random.Random(base_seed * 1_000_003 + index))
    puzzle = generator.generate_sudoku(symmetric=True)
    result = Solver(CONFIG).solve(puzzle)
    if not result.solved:
        return index, None, puzzle, []
    used = {step.type for step in result.steps}
    group = "subsets" if used & SUBSETS else "intersections" if used & INTERSECTIONS else "singles"
    return index, group, puzzle, sorted(t.name for t in used)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--per-group", type=int, default=100)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--max-attempts", type=int, default=40_000)
    args = parser.parse_args()

    bank: dict[str, list[str]] = {g: [] for g in GROUPS}
    coverage: Counter[str] = Counter()
    unsolved = 0
    attempts = 0

    jobs = ((i, args.seed) for i in range(args.max_attempts))
    with multiprocessing.Pool() as pool:
        # imap conserva el orden de los índices: el banco no depende del reparto entre procesos.
        for index, group, puzzle, used in pool.imap(classify, jobs, chunksize=16):
            attempts = index + 1
            if group is None:
                unsolved += 1
            elif len(bank[group]) < args.per_group:
                bank[group].append(puzzle)
                coverage.update(used)
            if all(len(bank[g]) >= args.per_group for g in GROUPS):
                pool.terminate()
                break

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for group in GROUPS:
        header = (
            f"# Banco de puzzles: {group}. Generado con tools/oracle/generate_bank.py\n"
            f"# hodoku-py {version('hodoku-py')} · semilla {args.seed} · un puzzle por línea ('0' = vacía)\n"
        )
        (OUTPUT_DIR / f"{group}.txt").write_text(header + "".join(p + "\n" for p in bank[group]))

    print(f"Intentos: {attempts} · sin resolver con la Fase 1: {unsolved}")
    for group in GROUPS:
        print(f"  {group:<14} {len(bank[group])}")
    print("Puzzles del banco que usan cada técnica:")
    for step in DEFAULT_CONFIG.solver_steps:
        if step.solution_type in PHASE_1:
            print(f"  {step.solution_type.name:<22} {coverage[step.solution_type.name]}")


if __name__ == "__main__":
    main()
