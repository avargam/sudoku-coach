"""Medida de la lectura en vivo (tarea 4.1): lecturas de fotogramas frente a la foto del mismo tablero.

Lee las líneas `LiveMeasure: read ...` que escribe `FrameAnalyzer` con la app abierta en modo medida
y las compara con la etiqueta de la foto del mismo tablero (el id se da o se busca la etiqueta que
más se parece) y con la lectura de esa foto
en el A57 (`testdata/vision/results/mlkit-SM-A576B.json`, tarea 2.8). Solo biblioteca estándar.

    adb shell am start -n dev.sudokucoach/.app.MainActivity --ez measure true
    adb logcat -d -s LiveMeasure > tablero.log
    python3 live_measure.py tablero.log [001]

Sospechosa: celda con tinta sin leer o con confianza < 0,5, como en la revisión (D-08).
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
THRESHOLD = 50  # confianza en centésimas
READ = re.compile(r"read ms=(\d+) digits=(\d{81}) conf=([\d,]+) inked=([.#]{81}) origin=([.PM]{81})")


def parse(log):
    reads = []
    for line in Path(log).read_text().splitlines():
        m = READ.search(line)
        if m:
            reads.append({
                "ms": int(m[1]),
                "digits": [int(c) for c in m[2]],
                "conf": [int(c) for c in m[3].split(",")],
                "inked": [c == "#" for c in m[4]],
            })
    return reads


def label(photo):
    lines = [l for l in (ROOT / f"testdata/vision/labels/{photo}.txt").read_text().splitlines() if l and not l.startswith("#")]
    return [0 if c == "." else int(c) for c in "".join(lines[:9])]


def photo_reading(photo):
    results = json.loads((ROOT / "testdata/vision/results/mlkit-SM-A576B.json").read_text())
    p = next(p for p in results["photos"] if p["id"] == photo)
    digits = [0 if c == "." else int(c) for c in p["grid"]["digits"]]
    conf = [round(c * 100) for c in p["grid"]["confidence"]]
    inked = [c == "0" for c in p["empty"]]
    return {"digits": digits, "conf": conf, "inked": inked}


def mode(reads):
    """El valor más leído en cada celda, también las vacías: no lo desvían las lecturas sueltas."""
    return {"digits": [Counter(r["digits"][i] for r in reads).most_common(1)[0][0] for i in range(81)]}


def closest_photo(reading):
    """La foto cuya etiqueta coincide en más celdas leídas con [reading], si coincide en el 90 %."""
    read = [i for i in range(81) if reading["digits"][i]]
    best, score = None, 0
    for path in sorted((ROOT / "testdata/vision/labels").glob("*.txt")):
        truth = label(path.stem)
        hits = sum(reading["digits"][i] == truth[i] for i in read)
        if hits > score:
            best, score = path.stem, hits
    if best and score >= 0.9 * len(read):
        print(f"tablero: foto {best} ({score}/{len(read)} celdas leídas coinciden)")
        return best
    print("tablero: ninguna etiqueta coincide")
    return None


def suspicious(r):
    return {i for i in range(81) if r["inked"][i] and (r["digits"][i] == 0 or r["conf"][i] < THRESHOLD)}


def errors(r, truth):
    return {i for i in range(81) if r["digits"][i] != truth[i] and (r["inked"][i] or truth[i])}


def vote(reads):
    """Votación simple, solo para una primera respuesta: mayoría por celda; sospechosa si no hay
    unanimidad entre las lecturas con tinta o si ninguna lectura del valor ganador es segura."""
    voted = {"digits": [], "conf": [], "inked": []}
    split = set()
    for i in range(81):
        inked = [r for r in reads if r["inked"][i]]
        if len(inked) * 2 <= len(reads):
            voted["digits"].append(0); voted["conf"].append(100); voted["inked"].append(False)
            continue
        counts = Counter(r["digits"][i] for r in inked)
        digit, n = counts.most_common(1)[0]
        best = max(r["conf"][i] for r in inked if r["digits"][i] == digit)
        if len(counts) > 1:
            split.add(i)
        unanimous = n == len(inked)
        voted["digits"].append(digit)
        voted["conf"].append(best if unanimous else 0)
        voted["inked"].append(True)
    return voted, split


def describe(name, r, truth):
    s = suspicious(r)
    line = f"{name}: {len(s)} sospechosas"
    if truth:
        e = errors(r, truth)
        line += f", {len(e)} errores, {len(e - s)} sin marcar {sorted(e - s) or ''}"
    return line


def main():
    log = sys.argv[1]
    reads = parse(log)
    if not reads:
        sys.exit("No hay lecturas en el registro")
    photo = sys.argv[2] if len(sys.argv) > 2 else closest_photo(mode(reads))
    truth = label(photo) if photo else None
    ms = sorted(r["ms"] for r in reads)
    print(f"{len(reads)} lecturas, {ms[len(ms) // 2]} ms de mediana")
    for k, r in enumerate(reads):
        print("  " + describe(f"fotograma {k + 1}", r, truth))
    if photo:
        print(describe(f"foto {photo}", photo_reading(photo), truth))
    voted, split = vote(reads)
    print(describe("votación", voted, truth))
    print(f"celdas con lecturas distintas entre fotogramas: {len(split)}")
    if truth:
        always = [i for i in range(81) if all(r["digits"][i] != truth[i] for r in reads if r["inked"][i]) and any(r["inked"][i] for r in reads)]
        same = [i for i in always if len({r["digits"][i] for r in reads if r["inked"][i]}) == 1]
        print(f"celdas mal en todos los fotogramas: {len(always)}, siempre con el mismo valor: {len(same)}")
        for i in same:
            r0 = next(r for r in reads if r["inked"][i])
            print(f"  F{i // 9 + 1}C{i % 9 + 1}: {truth[i]} leído como {r0['digits'][i]}")


if __name__ == "__main__":
    main()
