"""Prepara las fotos de prueba para versionarlas (tarea 2.1, D-17).

Lee los originales de `testdata/vision/raw/` (ignorado por git) y escribe en `testdata/vision/photos/`:
- aplica la orientación del EXIF (el móvil guarda la foto girada y la rota al mostrarla);
- reescala para que el lado mayor mida 1600 px;
- guarda en JPEG **sin EXIF**, que puede incluir la ubicación GPS, porque el repositorio será público.

Las fotos se numeran 001, 002... por orden de nombre del original (la fecha de la cámara).
`testdata/vision/sources.csv` guarda la correspondencia, y una foto ya preparada conserva su número.

Uso:
    python prepare_photos.py
"""

from __future__ import annotations

import csv
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2] / "testdata/vision"
RAW, PHOTOS, SOURCES = ROOT / "raw", ROOT / "photos", ROOT / "sources.csv"
LONG_SIDE = 1600
QUALITY = 85


def load_sources() -> dict[str, str]:
    """Correspondencia original → número ya asignado, para no renumerar al añadir fotos."""
    if not SOURCES.exists():
        return {}
    with SOURCES.open(newline="") as f:
        return {row["original"]: row["id"] for row in csv.DictReader(f)}


def main() -> None:
    PHOTOS.mkdir(parents=True, exist_ok=True)
    sources = load_sources()
    next_id = max((int(i) for i in sources.values()), default=0) + 1
    originals = sorted(p for p in RAW.iterdir() if p.suffix.lower() in (".jpg", ".jpeg"))

    for original in originals:
        if original.name not in sources:
            sources[original.name] = f"{next_id:03d}"
            next_id += 1
        target = PHOTOS / f"{sources[original.name]}.jpg"
        with Image.open(original) as image:
            image = ImageOps.exif_transpose(image).convert("RGB")
            image.thumbnail((LONG_SIDE, LONG_SIDE), Image.Resampling.LANCZOS)
            # Sin exif=...: Pillow no copia los metadatos al guardar.
            image.save(target, "JPEG", quality=QUALITY, optimize=True)
        print(f"{original.name} → {target.name} ({image.width}×{image.height}, {target.stat().st_size // 1024} KiB)")

    with SOURCES.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "original"])
        for name, photo_id in sorted(sources.items(), key=lambda kv: kv[1]):
            writer.writerow([photo_id, name])


if __name__ == "__main__":
    main()
