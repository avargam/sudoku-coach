"""Rellena las plantillas de etiqueta de las secuencias con lo más leído por ML Kit (tarea 4.4).

Para cada secuencia de testdata/vision/sequences/ con label.txt y frames.txt (de SequenceScanTest),
cambia cada `?` de la plantilla por el dígito más leído en esa celda entre todos los fotogramas, y
lo deja en `?` si ninguna lectura da dígito. Después comprueba cada etiqueta: que no haya dígitos
repetidos en filas, columnas y cajas. Es solo un borrador: los dígitos se revisan a mano contra la
imagen de revisión (vision-core/build/labeling/<id>.png), sobre todo los manuscritos.

    python3 tools/vision/prefill_labels.py [S20261025-205443 ...]
"""

import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "testdata/vision/sequences"


def majority(frames_txt):
    counts = [Counter() for _ in range(81)]
    for line in frames_txt.read_text().splitlines()[1:]:
        p = line.split(" ")
        if len(p) > 4:
            for i, c in enumerate(p[4]):
                if c != "0":
                    counts[i][c] += 1
    return [c.most_common(1)[0][0] if c else "?" for c in counts]


def repeats(digits):
    units = [[r * 9 + c for c in range(9)] for r in range(9)] + [[r * 9 + c for r in range(9)] for c in range(9)]
    units += [[(br * 3 + r) * 9 + bc * 3 + c for r in range(3) for c in range(3)] for br in range(3) for bc in range(3)]
    bad = set()
    for unit in units:
        seen = Counter(digits[i] for i in unit if digits[i] not in ".?")
        bad |= {i for i in unit if seen.get(digits[i], 0) > 1}
    return sorted(bad)


def main():
    only = set(sys.argv[1:])
    for d in sorted(ROOT.glob("S*")):
        label, frames = d / "label.txt", d / "frames.txt"
        if (only and d.name not in only) or not label.exists():
            continue
        lines = label.read_text().splitlines()
        cells = list("".join(lines[1:10]))
        if frames.exists() and "?" in cells:
            guess = majority(frames)
            cells = [guess[i] if c == "?" else c for i, c in enumerate(cells)]
            lines[1:10] = ["".join(cells[r * 9:r * 9 + 9]) for r in range(9)]
            label.write_text("\n".join(lines) + "\n")
        bad = repeats(cells)
        pending = cells.count("?")
        print(f"{d.name}: {pending} pendientes, repetidos en {[f'F{i // 9 + 1}C{i % 9 + 1}' for i in bad] or 'ninguna'}")


if __name__ == "__main__":
    main()
