# 04 · Plan de implementación

Cada fase termina con algo que se puede ejecutar y probar. Marca las casillas al avanzar.

El detalle de las Fases 0 y 1 (tareas, orden y criterios de cada una) está en [05-plan-fase-0-1.md](05-plan-fase-0-1.md).

## Fase 0 · Preparación

- [x] Esqueleto del repositorio y documentación
- [x] Módulo `solver` con `Grid`, `Step`, `Naked Single`, `Hidden Single` y tests
- [x] `git init`
- [x] Decidir licencia (D-10: Apache-2.0)
- [x] JDK 21 en WSL y Gradle wrapper (D-15)
- [x] Archivo `LICENSE`, primer commit y repositorio remoto privado en GitHub
- [x] CI básico (GitHub Actions: `./gradlew :solver:test`)
- [x] Configurar `tools/oracle` (Python + `hodoku-py`)

## Fase 1 · Motor lógico (`solver`)

Objetivo: dado un tablero, devolver el siguiente paso con técnicas hasta subconjuntos (niveles 1–3).

- [x] `Step` estructurado con `TechniqueId` y categoría (D-14)
- [x] Backtracking de referencia + comprobación de unicidad
- [x] Banco de puzzles generado con `hodoku-py`
- [x] Test de propiedad: todo paso es compatible con la solución
- [x] Full House
- [x] Locked Candidates (Pointing / Claiming)
- [x] Naked Pair/Triple/Quad y Locked Pair/Triple
- [x] Hidden Pair/Triple/Quad
- [x] Orden de HoDoKu por defecto, configurable (D-13)
- [x] Oráculo: pasos esperados exportados a JSON y test de comparación

**Cerrada:** 300/300 puzzles del banco resueltos y 16 753/16 753 pasos coincidentes con HoDoKu. reglib (1.11, opcional) se pospone a la Fase 6.

**Hecho cuando:** resuelve todos los puzzles del banco que HoDoKu resuelve con esas técnicas, coincide con HoDoKu en la técnica elegida en cada paso y la CI está en verde.

## Fase 2 · Visión sobre fotos fijas (`vision-core` + `vision`)

Objetivo: de una foto a un tablero 9×9 de enteros. Detalle en [06-plan-fase-2.md](06-plan-fase-2.md).

- [x] Decidir la separación de `vision` (D-16: `vision-core` JVM + `vision` Android)
- [x] 2.0 Android SDK en WSL y Galaxy A57 por USB con usbipd-win (D-15)
- [x] 2.1 Reunir 30–50 fotos y prepararlas (`testdata/vision`, D-17)
- [x] 2.2 Módulo `vision-core` con OpenCV 4.14 (Bytedeco) y tests en la CI
- [x] 2.3 Detección de tablero + homografía + validación geométrica
- [x] 2.4 Segmentación de celdas y detección de celda vacía
- [x] 2.5 Etiquetado asistido y validación de etiquetas con el `solver`
- [x] 2.6 Métricas en la JVM: detección y celdas vacías
- [x] 2.7 Módulo `vision` (Android) con OpenCV 4.14 y `MlKitDigitRecognizer`
- [x] 2.8 Métricas en el móvil: acierto por celda (impresos y manuscritos)
- [x] 2.9 Cierre: revisar D-07 y D-08

**Cerrada:** tablero detectado en 30/30 fotos y vacía/no vacía correcta en el 99,8 % de las celdas. Error por celda con ML Kit en el A57: impresos 0 % (746/746), manuscritos 5,4 % (79/1455, 29 sin lectura y 50 equivocados); con el umbral de confianza 0,5 ningún dígito equivocado se da por bueno. D-07 y D-08 aceptadas; las cifras vienen de un solo libro, así que las fotos difíciles se miden en la Fase 4.

**Hecho cuando:** se cumple el criterio de detección ≥ 90 % y se conoce la tasa de error por celda.

## Fase 3 · App mínima de extremo a extremo (`app`)

Objetivo: elegir una foto → revisar → ver la primera pista. Detalle en [08-plan-fase-3.md](08-plan-fase-3.md).

- [x] Decidir la arquitectura de la app (D-18) y la validación antes de las pistas (D-19)
- [x] 3.0 Proyecto `app` con Compose, Navigation 3 y `ViewModel`; CI con la app
- [x] 3.1 Tema propio con los tokens de color de `03-diseno.md` §5.3, en claro y oscuro
- [ ] 3.2 Componente `SudokuBoard` reutilizable (dígitos por origen, resaltados, mini-rejilla de candidatos)
- [ ] 3.3 Lectura de la foto de galería (orientación, 1600 px, origen en `vision-core`)
- [ ] 3.4 Tablero de revisión, celdas sospechosas y validación con el `solver`
- [ ] 3.5 Pantalla de revisión editable: selección de celda, teclado numérico fijo, celdas sospechosas con contador
- [ ] 3.6 Textos de explicación en español por `TechniqueId`, como recursos de Android (D-14)
- [ ] 3.7 Pantalla de pista: tablero + bottom sheet con revelado gradual (técnica → celdas → resultado) y "aplicar paso"
- [ ] 3.8 Estado y navegación, con el tablero guardado ante el cierre del proceso
- [ ] 3.9 Prueba de extremo a extremo y recorrido con las 30 fotos

**Hecho cuando:** se puede usar la app con fotos de galería de principio a fin.

## Fase 4 · Cámara en vivo

- [ ] CameraX con análisis de fotogramas
- [ ] Escáner a pantalla completa con contorno del tablero en el acento y captura automática
- [ ] Votación multi-frame y confianza por celda
- [ ] Rendimiento: presupuesto de tiempo por fotograma
- [ ] Fotos difíciles (otro papel, sombra fuerte, ángulo > 15°, curvatura) añadidas a `testdata/vision` y medidas; reabrir D-07 (Hough) si la detección baja del 90 %

## Fase 5 · CNN propia

- [ ] Generador de dataset sintético (fuentes, ruido, desenfoque)
- [ ] Etiquetar celdas de fotos propias
- [ ] Entrenar y evaluar en Python; exportar a LiteRT
- [ ] `TfLiteDigitRecognizer`
- [ ] Comparar contra ML Kit con el mismo conjunto de fotos y decidir (D-08)
- [ ] Clasificar el origen de cada dígito (impreso / manuscrito) para la tipografía de D-12; el segmentador ya lo propone por contraste (99,9 % en la Fase 2), así que la CNN solo lo sustituye si mejora

## Fase 6 · Técnicas avanzadas

Una familia por iteración, cada una con tests y verificación contra el oráculo:

- [ ] reglib de HoDoKu en local (tarea 1.11 pospuesta): casos de regresión por técnica, útiles sobre todo para las familias avanzadas
- [ ] Ampliar el banco y `export_steps.py` a las técnicas nuevas

- [ ] X-Wing, Swordfish, Jellyfish
- [ ] XY-Wing, XYZ-Wing, W-Wing
- [ ] Unique Rectangle
- [ ] Coloración simple
- [ ] Cadenas (AIC) y, si procede, ALS

## Fase 7 · Pulido y publicación

- [ ] Overlay sobre la cámara con homografía inversa
- [ ] Revisión de accesibilidad (contraste, TalkBack, tamaño de fuente)
- [ ] README con capturas y demo
- [ ] Licencia final y publicación en GitHub

## Primeros pasos concretos (próxima sesión)

Ver la sección "Orden de trabajo" de [05-plan-fase-0-1.md](05-plan-fase-0-1.md). En paralelo se puede empezar a fotografiar sudokus para la Fase 2, que es lo que más tarda.
