"""Exporta los pasos de HoDoKu sobre el banco de puzzles para el OracleTest de Kotlin (tarea 1.10).

Para cada puzzle reproduce el bucle de resolución de HoDoKu con las técnicas de la Fase 1 y, en
cada estado, guarda el paso elegido y todas las instancias de esa técnica que HoDoKu encuentra
(`find_all`). Así el test puede exigir la misma técnica y aceptar cualquier instancia válida.

Cada instancia se codifica como texto compacto `celdas|colocaciones|eliminaciones`, p. ej.
`9,63||27-3,54-3,72-3` (Naked Pair) o `40|40=5|` (Full House).

Uso:
    python export_steps.py      # lee solver/src/test/resources/bank/ y escribe .../oracle/
"""

from __future__ import annotations

import json
import multiprocessing
import sys
from importlib.metadata import version
from pathlib import Path

from hodoku.core.grid import Grid
from hodoku.core.types import SolutionType
from hodoku.solver.solver import _apply_step
from hodoku.solver.step_finder import SudokuStepFinder

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_bank import CONFIG, GROUPS, OUTPUT_DIR as BANK_DIR, PHASE_1  # noqa: E402

ORACLE_DIR = BANK_DIR.parent / "oracle"
PLACEMENT_TYPES = {SolutionType.FULL_HOUSE, SolutionType.NAKED_SINGLE, SolutionType.HIDDEN_SINGLE}


def encode(step) -> str:
    """Instancia de un paso como `celdas|colocaciones|eliminaciones`."""
    cells = ",".join(str(i) for i in step.indices)
    placements = ""
    if step.type in PLACEMENT_TYPES:
        placements = ",".join(f"{i}={v}" for i, v in zip(step.indices, step.values))
    eliminations = ",".join(f"{c.index}-{c.value}" for c in step.candidates_to_delete)
    return f"{cells}|{placements}|{eliminations}"


def export_puzzle(puzzle: str) -> dict:
    """Camino de HoDoKu para un puzzle, con las instancias alternativas de cada paso."""
    grid = Grid()
    grid.set_sudoku(puzzle)
    finder = SudokuStepFinder(grid, CONFIG.solve_search)
    steps = []
    while not grid.is_solved():
        step = next((s for cfg in CONFIG.solver_steps if (s := finder.get_step(cfg.solution_type)) is not None), None)
        if step is None:
            raise RuntimeError(f"HoDoKu no resuelve {puzzle} con la Fase 1; el banco no debería contenerlo")
        options: list[str] = []
        for other in SudokuStepFinder(grid, CONFIG.find_all_search).find_all(step.type):
            code = encode(other)
            if code not in options:
                options.append(code)
        chosen = encode(step)
        if chosen not in options:
            options.append(chosen)
        steps.append({"t": step.type.name, "chosen": options.index(chosen), "options": options})
        _apply_step(grid, step)
    return {"puzzle": puzzle, "steps": steps}


def main() -> None:
    order = [cfg.solution_type.name for cfg in CONFIG.solver_steps if cfg.solution_type in PHASE_1]
    ORACLE_DIR.mkdir(parents=True, exist_ok=True)
    with multiprocessing.Pool() as pool:
        for group in GROUPS:
            lines = (BANK_DIR / f"{group}.txt").read_text().splitlines()
            puzzles = [line.strip() for line in lines if line.strip() and not line.startswith("#")]
            exported = pool.map(export_puzzle, puzzles, chunksize=4)
            data = {
                "source": "tools/oracle/export_steps.py",
                "hodoku": version("hodoku-py"),
                "order": order,
                "puzzles": exported,
            }
            path = ORACLE_DIR / f"{group}.json"
            # Un puzzle por línea: el fichero sigue siendo JSON válido y los diffs quedan legibles.
            body = ",\n".join(json.dumps(p, separators=(",", ":")) for p in exported)
            header = json.dumps({k: v for k, v in data.items() if k != "puzzles"}, separators=(",", ":"))[:-1]
            path.write_text(f'{header},"puzzles":[\n{body}\n]}}\n')
            json.loads(path.read_text())  # comprobación de que el formato manual es JSON válido
            n_steps = sum(len(p["steps"]) for p in exported)
            print(f"{group:<14} {len(exported)} puzzles · {n_steps} pasos · {path.stat().st_size / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
