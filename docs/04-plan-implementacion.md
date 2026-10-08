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
- [ ] 2.0 Android SDK en WSL y Galaxy A57 por USB con usbipd-win (D-15)
- [ ] 2.1 Reunir 30–50 fotos y prepararlas (`testdata/vision`, D-17)
- [ ] 2.2 Módulo `vision-core` con OpenCV 4.14 (Bytedeco) y tests en la CI
- [ ] 2.3 Detección de tablero + homografía + validación geométrica
- [ ] 2.4 Segmentación de celdas y detección de celda vacía
- [ ] 2.5 Etiquetado asistido y validación de etiquetas con el `solver`
- [ ] 2.6 Métricas en la JVM: detección y celdas vacías
- [ ] 2.7 Módulo `vision` (Android) con OpenCV 4.14 y `MlKitDigitRecognizer`
- [ ] 2.8 Métricas en el móvil: acierto por celda (impresos y manuscritos)
- [ ] 2.9 Cierre: revisar D-07 y D-08

**Hecho cuando:** se cumple el criterio de detección ≥ 90 % y se conoce la tasa de error por celda.

## Fase 3 · App mínima de extremo a extremo (`app`)

Objetivo: elegir una foto → revisar → ver la primera pista.

- [ ] Proyecto `app` con Compose, navegación y `ViewModel`
- [ ] Tema propio con los tokens de color de `03-diseno.md` §5.3, en claro y oscuro
- [ ] Componente `SudokuBoard` reutilizable (dígitos por origen, resaltados, mini-rejilla de candidatos)
- [ ] Pantalla de revisión editable: selección de celda, teclado numérico fijo, celdas sospechosas con contador
- [ ] Pantalla de pista: tablero + bottom sheet con revelado gradual (técnica → celdas → resultado)
- [ ] Textos de explicación en español por `TechniqueId`, como recursos de Android (D-14)
- [ ] Botón "aplicar paso" para encadenar pistas

**Hecho cuando:** se puede usar la app con fotos de galería de principio a fin.

## Fase 4 · Cámara en vivo

- [ ] CameraX con análisis de fotogramas
- [ ] Escáner a pantalla completa con contorno del tablero en el acento y captura automática
- [ ] Votación multi-frame y confianza por celda
- [ ] Rendimiento: presupuesto de tiempo por fotograma

## Fase 5 · CNN propia

- [ ] Generador de dataset sintético (fuentes, ruido, desenfoque)
- [ ] Etiquetar celdas de fotos propias
- [ ] Entrenar y evaluar en Python; exportar a LiteRT
- [ ] `TfLiteDigitRecognizer`
- [ ] Comparar contra ML Kit con el mismo conjunto de fotos y decidir (D-08)
- [ ] Clasificar el origen de cada dígito (impreso / manuscrito) para la tipografía de D-12

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
