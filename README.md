# Sudoku Coach

App Android que escanea un sudoku en papel con la cámara, reconoce el estado actual del tablero y muestra **el siguiente paso lógico** a seguir, indicando la técnica humana que lo justifica (Naked Single, X-Wing, XY-Wing, etc.) y explicándola.

> Estado: **Fase 1 completada**. El módulo `solver` implementa las 13 técnicas hasta subconjuntos (singles, intersecciones, subconjuntos desnudos y ocultos) y coincide con HoDoKu en todos los pasos de un banco de 300 puzzles. `vision` y `app` están por implementar.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-problema.md](docs/01-problema.md) | Descripción del problema, alcance y criterios de éxito |
| [docs/02-decisiones.md](docs/02-decisiones.md) | Decisiones tomadas (ADR) y decisiones pendientes |
| [docs/03-diseno.md](docs/03-diseno.md) | Outline del diseño de la solución |
| [docs/04-plan-implementacion.md](docs/04-plan-implementacion.md) | Pasos a seguir, por fases |
| [docs/05-plan-fase-0-1.md](docs/05-plan-fase-0-1.md) | Plan detallado de las Fases 0 (preparación) y 1 (motor lógico) |
| [docs/06-plan-fase-2.md](docs/06-plan-fase-2.md) | Plan detallado de la Fase 2 (visión sobre fotos fijas) |

## Estructura del repositorio

```
sudoku-coach/
├── docs/            Documentación de diseño y planificación
├── solver/          Motor de técnicas (Kotlin/JVM puro, sin Android) ← implementado parcialmente
├── vision/          Detección del tablero y reconocimiento de dígitos (Android + OpenCV + LiteRT) ← pendiente
├── app/             Aplicación Android (CameraX, UI, overlay) ← pendiente
└── tools/oracle/    Scripts para contrastar el solver contra HoDoKu (hodoku-py)
```

## Requisitos (Ubuntu)

- JDK 21 (`sudo apt install openjdk-21-jdk`)
- Gradle: no hace falta instalarlo; se usa el wrapper `./gradlew`
- Para `vision` y `app`: Android SDK (command-line tools) en WSL y un dispositivo conectado por USB (en WSL, con usbipd-win); Android Studio es opcional (ver [D-15](docs/02-decisiones.md))

## Probar el solver

```bash
./gradlew :solver:test
```

## Licencia

[Apache-2.0](LICENSE) (ver [decisión D-10](docs/02-decisiones.md)).
