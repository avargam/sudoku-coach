# Herramientas de visión

Scripts de apoyo para el conjunto de fotos de prueba (D-17). Ver `testdata/vision/README.md`.

```bash
cd tools/vision
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python prepare_photos.py      # raw/ → photos/ (orientación aplicada, 1600 px, sin EXIF)
```

Para traer las fotos del móvil conectado por USB (D-15) a `testdata/vision/raw/` (ignorado por git):

```bash
adb pull /sdcard/DCIM/Sudoku/. testdata/vision/raw/
```

`prepare_photos.py` aplica la orientación del EXIF **antes** de quitarlo: el móvil guarda la foto girada
y solo la endereza al mostrarla, así que sin ese paso las fotos quedarían de lado.
