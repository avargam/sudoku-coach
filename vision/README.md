# vision (pendiente)

Módulo Android library. Responsabilidades (ver [docs/03-diseno.md](../docs/03-diseno.md), sección 4):

- Detección del tablero y homografía (OpenCV)
- Segmentación en 81 celdas
- `DigitRecognizer`: ML Kit (MVP) y CNN en LiteRT (objetivo)
- `testdata/`: fotos etiquetadas para medir detección y reconocimiento

Se crea en la Fase 2 del [plan](../docs/04-plan-implementacion.md), desde Android Studio, y después se activa en `settings.gradle.kts`.
