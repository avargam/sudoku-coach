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

Para medir la lectura en vivo (tarea 4.1), con la app de debug en el móvil y el tablero de una de las
fotos delante de la cámara:

```bash
adb shell am start -n dev.sudokucoach/.app.MainActivity --ez measure true
adb logcat -c                     # con el tablero ya en el visor; esperar unos segundos
adb logcat -d -s LiveMeasure > tablero.log
python3 live_measure.py tablero.log [030]   # sin id, busca la etiqueta más parecida
```

Secuencias grabadas en vivo (D-22, tarea 4.4), con la app de debug: botón **Grabar** del escáner,
una secuencia por página (algo de movimiento al principio y 3–5 s quieto; se para sola a los 8 s).

```bash
tools/vision/pull_sequences.sh                                       # móvil → testdata/vision/sequences/
./gradlew :vision-core:labelHelper --args="--sequences"              # plantilla label.txt + vision-core/build/labeling/<id>.png
python3 tools/vision/prefill_labels.py                               # con frames.txt: rellena los ? con lo más leído
tools/vision/push_labels.sh                                          # etiquetas → móvil
adb shell am instrument -w -e class dev.sudokucoach.app.scan.SequenceScanTest \
    dev.sudokucoach.test/androidx.test.runner.AndroidJUnitRunner     # lector real; resumen en logcat (SequenceScanTest)
tools/vision/pull_sequences.sh                                       # trae frames.txt (lectura de cada fotograma)
./gradlew :app:testDebugUnitTest --tests "*SequenceReplayTest*"      # reproduce en la JVM con frames.txt
```

Los fotogramas se quedan en el móvil (`files/sequences` de la app), que es de donde los lee el test:
enviar decenas de MB por usbipd se atasca. La plantilla se rellena con la etiqueta de una foto si el
patrón de tinta y de impresos coincide. Si no, la primera pasada de SequenceScanTest (sin etiquetas)
deja `frames.txt`, `prefill_labels.py` rellena los `?` con lo más leído por ML Kit y avisa de los
dígitos repetidos, y cada etiqueta se revisa contra la imagen de revisión: la mayoría de ML Kit suele
fallar en los 1 manuscritos (leídos como 4) y no ve los dígitos que el segmentador no recorta.

`prepare_photos.py` aplica la orientación del EXIF **antes** de quitarlo: el móvil guarda la foto girada
y solo la endereza al mostrarla, así que sin ese paso las fotos quedarían de lado.

Rendimiento en vivo (tarea 4.7), con la app de debug en el escáner: tiempos por fotograma, retraso
desde el sensor, de quieto a la revisión, fotogramas entrecortados, temperatura y consumo estimado.

```bash
tools/vision/perf_session.sh start    # marca el inicio; el móvil pasa a "desenchufado" para batterystats
# ... escanear varias páginas, volviendo de la revisión con "Escanear otro sudoku" ...
tools/vision/perf_session.sh stop     # resumen y log; devuelve el estado de la batería
```
