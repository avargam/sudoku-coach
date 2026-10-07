# 01 · Descripción del problema

## Contexto

Un sudoku resuelto "a la humana" avanza mediante pasos lógicos que corresponden a técnicas con nombre (Naked Single, Hidden Pair, Pointing, X-Wing, XY-Wing, Unique Rectangle, cadenas, etc.). Cuando alguien se atasca con un sudoku en papel, las opciones habituales son rendirse, buscar la solución completa (lo que arruina el aprendizaje) o transcribir el tablero a una herramienta digital a mano.

## Problema

> Dado un sudoku **físico** (impreso, con pistas impresas y dígitos escritos a mano por el usuario), la app debe **reconocer el estado actual del tablero con la cámara** y mostrar **el siguiente paso lógico**, con la técnica que lo justifica y una explicación, sin revelar la solución completa.

## Alcance (v1)

**Incluye**

- Plataforma Android, nativo (Kotlin).
- Procesamiento 100 % en el dispositivo (sin internet, sin servidor).
- Sudoku clásico 9×9 con una única solución.
- Dígitos impresos y manuscritos mezclados.
- El usuario **no** anota candidatos en el papel: la app los calcula.
- Catálogo de técnicas hasta nivel **avanzado** (ver `03-diseno.md`).
- Pantalla de revisión/corrección del tablero reconocido antes de calcular la pista.
- Explicación de cada pista (técnica, celdas implicadas, qué se coloca o elimina).

**No incluye (por ahora)**

- iOS.
- Variantes de sudoku (diagonal, killer, jigsaw, 16×16).
- Reconocimiento de candidatos escritos a mano en el papel.
- Resolución automática completa del puzzle ni generación de puzzles.
- Cuentas de usuario, sincronización, publicidad.

## Criterios de éxito

| Criterio | Objetivo inicial |
|---|---|
| Detección del tablero en condiciones razonables (luz normal, ángulo moderado) | ≥ 90 % de las fotos de prueba |
| Celdas mal reconocidas tras la corrección automática | < 1 % |
| Tiempo desde captura hasta pista | < 2 s en un teléfono de gama media |
| Corrección del motor lógico | 100 % de las pistas válidas (nunca sugerir un paso incorrecto sobre un tablero correcto) |
| Cobertura de técnicas | Singles, subconjuntos, intersecciones, fish, wings, unicidad, coloración/cadenas |

## Riesgos principales

1. **Lectura errónea de un dígito → pista incorrecta.** Es el mayor riesgo de UX. Mitigación: validación de reglas, votación multi-frame, confianza por celda y pantalla de revisión.
2. **Dígitos manuscritos** (1/7, 4/9, 0 frente a vacío). Mitigación: CNN propia entrenada con datos reales del usuario.
3. **Detección del tablero** con iluminación mala, sombras o fondo complejo. Mitigación: umbral adaptativo, validación geométrica de la rejilla.
4. **Complejidad de las técnicas avanzadas** (cadenas, ALS). Mitigación: implementación incremental con un oráculo (HoDoKu) para contrastar.
5. **Licencias**: HoDoKu es GPL-3.0; condiciona qué código se puede incorporar.

## Glosario

- **Pista**: valor impreso (clue) o dígito colocado por el usuario.
- **Candidato**: dígito aún posible en una celda vacía.
- **Paso (step)**: una deducción aplicable en el estado actual: coloca un dígito y/o elimina candidatos.
- **Técnica**: patrón lógico que justifica un paso.
