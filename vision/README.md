# vision (pendiente)

Librería Android de la Fase 2 (ver [docs/06-plan-fase-2.md](../docs/06-plan-fase-2.md) y la decisión D-16):

- Conversión `Bitmap` ⇄ `Mat` con el AAR de OpenCV 4.14
- `MlKitDigitRecognizer`: ML Kit Text Recognition v2 con el modelo incluido en la app (MVP)
- `TfLiteDigitRecognizer`: CNN propia en LiteRT (Fase 5)

La detección del tablero, la rectificación y la segmentación viven en `vision-core` (Kotlin/JVM puro), que se prueba en la JVM con las fotos de `testdata/vision/`.
