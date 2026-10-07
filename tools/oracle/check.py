"""Comprueba la instalación de hodoku-py resolviendo un puzzle e imprimiendo sus pasos.

Uso:
    python check.py [puzzle]      # 81 caracteres, '0' o '.' para las celdas vacías
"""

import sys
from collections import Counter

from hodoku import Solver

# Puzzle de la maqueta (Naked Pair {1,3} en la columna 1 tras aplicar los singles).
DEFAULT_PUZZLE = "200300800070009060600007031020100090700000000006000103000010005000504002090800010"


def main() -> None:
    puzzle = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PUZZLE
    result = Solver().solve(puzzle)

    print(f"Resuelto: {result.solved} · nivel {result.level.name} · puntuación {result.score}")
    for i, step in enumerate(result.steps, 1):
        print(f"{i:3}. {step}")

    print("\nTécnicas usadas:")
    for name, count in Counter(step.type.name for step in result.steps).most_common():
        print(f"  {name:<22} {count}")


if __name__ == "__main__":
    main()
