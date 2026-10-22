# Sudoku Coach

App Android que escanea un sudoku en papel con la cámara, reconoce el estado actual del tablero y muestra **el siguiente paso lógico** a seguir, indicando la técnica humana que lo justifica (Naked Single, X-Wing, XY-Wing, etc.) y explicándola.

> Estado: **Fase 3 en curso** (app mínima de extremo a extremo). El `solver` implementa las 13 técnicas hasta subconjuntos y coincide con HoDoKu en los 16 753 pasos de un banco de 300 puzzles (Fase 1). `vision-core` y `vision` leen el tablero de una foto en el móvil: impresos 100 %, manuscritos 94,6 % (Fase 2). El módulo `app` tiene ya el esqueleto con Compose.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-problema.md](docs/01-problema.md) | Descripción del problema, alcance y criterios de éxito |
| [docs/02-decisiones.md](docs/02-decisiones.md) | Decisiones tomadas (ADR) y decisiones pendientes |
| [docs/03-diseno.md](docs/03-diseno.md) | Outline del diseño de la solución |
| [docs/04-plan-implementacion.md](docs/04-plan-implementacion.md) | Pasos a seguir, por fases |
| [docs/05-plan-fase-0-1.md](docs/05-plan-fase-0-1.md) | Plan detallado de las Fases 0 (preparación) y 1 (motor lógico) |
| [docs/06-plan-fase-2.md](docs/06-plan-fase-2.md) | Plan detallado de la Fase 2 (visión sobre fotos fijas) |
| [docs/07-puesta-en-marcha.md](docs/07-puesta-en-marcha.md) | Cómo retomar el entorno: WSL, móvil por USB, tests |
| [docs/08-plan-fase-3.md](docs/08-plan-fase-3.md) | Plan detallado de la Fase 3 (app mínima de extremo a extremo) |

## Estructura del repositorio

```
sudoku-coach/
├── docs/            Documentación de diseño y planificación
├── solver/          Motor de técnicas (Kotlin/JVM puro, sin Android)
├── vision-core/     Detección del tablero y segmentación de celdas (Kotlin/JVM + OpenCV)
├── vision/          Reconocimiento de dígitos en Android (OpenCV + ML Kit)
├── app/             Aplicación Android (Compose) ← en curso
├── testdata/vision/ Fotos de prueba etiquetadas y resultados del móvil
└── tools/           Oráculo contra HoDoKu (oracle/) y preparación de fotos (vision/)
```

## Requisitos (Ubuntu)

- JDK 21 (`sudo apt install openjdk-21-jdk`)
- Para los tests de `vision-core`: GTK2 (`sudo apt install libgtk2.0-0t64`), que piden los nativos de OpenCV
- Gradle: no hace falta instalarlo; se usa el wrapper `./gradlew`
- Para `vision` y `app`: Android SDK (command-line tools) en WSL y un dispositivo conectado por USB (en WSL, con usbipd-win); Android Studio es opcional (ver [D-15](docs/02-decisiones.md))

## Probar

```bash
./gradlew :solver:test :vision-core:test :app:testDebugUnitTest   # en la JVM, sin móvil
./gradlew :app:installDebug                                        # instala la app en el móvil conectado
```

Más detalle, incluidos los tests en el móvil, en [docs/07-puesta-en-marcha.md](docs/07-puesta-en-marcha.md).

## Licencia

[Apache-2.0](LICENSE) (ver [decisión D-10](docs/02-decisiones.md)).
