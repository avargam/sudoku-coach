# vision

Librería Android que lleva el pipeline de `vision-core` al móvil (D-16): OpenCV 4.14 (AAR oficial)
y reconocimiento de dígitos con ML Kit Text Recognition, con el modelo incluido en la app (D-03, D-08).

| Clase | Qué hace |
|---|---|
| `OpenCvAndroid` | Carga OpenCV y convierte `Bitmap` ⇄ `Mat` en gris |
| `MlKitDigitRecognizer` | `DigitRecognizer` celda a celda, para usar con `BoardReader` |
| `MlKitGridReader` | Una sola pasada de ML Kit sobre el tablero rectificado, sin la rejilla |

Las llamadas a ML Kit son síncronas: no se deben hacer desde el hilo principal.

## Tests instrumentados

Necesitan el móvil conectado por USB (ver D-15 en `docs/02-decisiones.md`):

```bash
adb devices                                  # debe aparecer el dispositivo como "device"
./gradlew :vision:connectedDebugAndroidTest
```

Si Gradle dice `No connected devices!` aunque `adb devices` lo muestre, `./gradlew --stop` y repetir.
La CI solo compila el módulo y los tests (`:vision:assembleDebug :vision:assembleDebugAndroidTest`).
