# vision

Librería Android que lleva el pipeline de `vision-core` al móvil (D-16): OpenCV 4.14 (AAR oficial)
y reconocimiento de dígitos con ML Kit Text Recognition, con el modelo incluido en la app (D-03, D-08).

| Clase | Qué hace |
|---|---|
| `OpenCvAndroid` | Carga OpenCV y convierte `Bitmap` ⇄ `Mat` en gris |
| `MlKitDigitRecognizer` | `DigitRecognizer` celda a celda (con `BoardRecognizer.cellByCell`); solo como referencia en las métricas |
| `MlKitGridReader` | `BoardRecognizer` que lee todos los dígitos en una sola llamada a ML Kit, redibujados en una "lámina" sin rejilla. Es el que usa la app con `BoardReader` |

Las llamadas a ML Kit son síncronas: no se deben hacer desde el hilo principal.

## Tests instrumentados

Necesitan el móvil conectado por USB (ver D-15 en `docs/02-decisiones.md`):

```bash
adb devices                                  # debe aparecer el dispositivo como "device"
./gradlew :vision:connectedDebugAndroidTest
```

`DeviceMetricsTest` solo se ejecuta si se pide, porque tarda unos 4 minutos: pasa las 30 fotos de
`testdata/vision/photos` (copiadas como assets del APK de test) por el pipeline con las dos
estrategias. Para medir y guardar el resultado en `testdata/vision/results/`:

```bash
./gradlew :vision:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.metrics=true :vision:saveDeviceMetrics
./gradlew :vision-core:test   # escribe vision-core/build/reports/vision/device.md
```

Si Gradle dice `No connected devices!` aunque `adb devices` lo muestre, `./gradlew --stop` y repetir.
La CI solo compila el módulo y los tests (`:vision:assembleDebug :vision:assembleDebugAndroidTest`).
