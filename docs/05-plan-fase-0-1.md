# 05 · Plan detallado: Fases 0 y 1

Plan de trabajo para dejar el entorno listo (Fase 0) y construir el motor lógico hasta subconjuntos (Fase 1). Desarrolla las casillas de `04-plan-implementacion.md` y se apoya en las decisiones D-09, D-10, D-13, D-14 y D-15.

Cada tarea indica qué se entrega y cuándo se considera terminada. Las tareas se hacen en el orden en que aparecen, salvo las marcadas como *paralelizables*.

## Punto de partida

- Repositorio con `git init`, sin commits.
- `solver` tiene `Grid`, `Units`, `Step` (con texto en español), `Technique`, `HintEngine`, `NakedSingle`, `HiddenSingle` y 5 tests. No se ha compilado nunca en esta máquina.
- WSL2 sin JDK ni Gradle. Python 3.12 disponible (`hodoku-py` necesita ≥ 3.11).

---

## Fase 0 · Preparación

### 0.1 JDK 21
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
java -version
```
**Hecho cuando:** `java -version` muestra 21.

### 0.2 Gradle wrapper y versiones
- La versión de Gradle de apt es demasiado antigua para JDK 21. Se descarga la distribución binaria de Gradle una sola vez (fuera del repositorio), se ejecuta `gradle wrapper --gradle-version <versión>` y se versionan `gradlew`, `gradlew.bat` y `gradle/wrapper/`.
- Elegir versiones compatibles entre sí y con el Android Gradle Plugin que se usará en la Fase 2: Gradle, plugin de Kotlin (hoy `2.0.21`; actualizar si la matriz de compatibilidad lo pide) y `kotlinx-serialization` (para leer los JSON del oráculo en los tests).
- `solver` compila con JDK 21 pero genera bytecode **JVM 17** (`jvmTarget` y `release` a 17), para que Android lo pueda consumir.

**Hecho cuando:** `./gradlew :solver:test` pasa los 5 tests existentes.

### 0.3 Licencia y README
- Añadir `LICENSE` con el texto de Apache-2.0 (copyright 2026, autor del proyecto).
- El README ya enlaza a `LICENSE` y usa `./gradlew`.

### 0.4 Primer commit y remoto
- Revisar el `.gitignore` (añadir `tools/oracle/reglib/`, ver 1.11).
- Primer commit con el esqueleto, la documentación y el wrapper.
- **Tú** creas en github.com un repositorio **privado y vacío** (sin README ni licencia, para evitar conflictos) y pasas la URL; después se añade `origin` y se hace push de `main`.

**Hecho cuando:** `main` está en GitHub.

### 0.5 CI
`.github/workflows/ci.yml`: en cada push y PR, `actions/checkout`, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle` y `./gradlew :solver:test`. Sin Python (D-09).

**Hecho cuando:** la primera ejecución de Actions está en verde.

### 0.6 Entorno del oráculo *(paralelizable con 0.5)*
```bash
sudo apt install -y python3-venv   # si falta
cd tools/oracle && python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt     # hodoku-py==0.2.1 fijado
```
- `requirements.txt` con la versión fijada.
- Script mínimo `check.py`: resuelve un puzzle e imprime la secuencia de técnicas; sirve para confirmar la instalación.
- Anotar en `tools/oracle/README.md` el **orden por defecto de técnicas** de `SolverConfig`, tal como lo informe la librería (se usa en 1.9). Resultado: ver `03-diseno.md` §3.2.

**Hecho cuando:** `python check.py` imprime una secuencia de pasos.

---

## Fase 1 · Motor lógico (niveles 1–3)

### 1.1 Modelo estructurado (D-14)
Cambio del modelo antes de escribir más técnicas, para no migrarlas después:
- `Category` y `TechniqueId` (ver `03-diseno.md` §3.1).
- `Step` sin `explanation`; con `technique: TechniqueId`, `highlight`, `units`, `digits`, `placement` y `eliminations`.
- `Step.describe()`: texto técnico corto para depurar y para los mensajes de los tests (p. ej. `NAKED_PAIR {1,3} col 1 [F2C1,F8C1] -3@F4C1,F7C1,F9C1`).
- `Units.unitName` sale del `solver` (los textos de usuario son de la app). `cellName` se queda como utilidad de depuración.
- Adaptar `NakedSingle`, `HiddenSingle` y los tests.
- `Grid`: añadir un constructor a partir de valores y candidatos explícitos (para reglib y para fijar estados en los tests) y `equals`/`hashCode` por contenido.

**Hecho cuando:** compila, los tests pasan y no queda texto para el usuario en `solver`.

### 1.2 Backtracking de referencia
`Backtracker` (en `main`, porque la Fase 6 lo usará para la unicidad):
- `solve(grid): IntArray?` y `countSolutions(grid, limit = 2): Int`.
- Heurística de celda con menos candidatos; suficiente para puzzles de 9×9.

**Tests:** puzzle con solución única, puzzle sin solución, puzzle con varias soluciones (`countSolutions == 2`).

### 1.3 Banco de puzzles
Script `tools/oracle/generate_bank.py`:
- Genera con el generador de `hodoku-py` y clasifica con `rate`.
- Se queda con los puzzles que HoDoKu resuelve **usando solo las técnicas de la Fase 1** y los reparte por la técnica más difícil necesaria (singles, intersecciones, subconjuntos).
- Tamaño inicial propuesto: ~300 puzzles (100 por grupo). Semilla fija para que sea reproducible.
- Salida: `solver/src/test/resources/bank/<grupo>.txt`, un puzzle de 81 caracteres por línea. Se versiona.

**Hecho cuando:** existen los tres ficheros y cada puzzle tiene solución única según el `Backtracker` (lo comprueba un test).

### 1.4 Test de propiedad
`StepConsistencyTest`: para cada puzzle del banco, resolver con `HintEngine.solveLogically` y comprobar en cada paso que:
- la colocación coincide con la solución;
- ninguna eliminación quita el dígito de la solución;
- el paso cambia algo (no es un paso vacío).

Hasta que estén todas las técnicas, el test valida los pasos que se den aunque el puzzle no se termine; el criterio de "resolver todo el banco" se comprueba en 1.10.

### 1.5 Full House
Última celda vacía de una unidad. Va antes que Naked Single (orden de HoDoKu).
**Tests:** tablero construido con una Full House; comprobar que se elige antes que un Naked Single presente a la vez.

### 1.6 Locked Candidates
- **Pointing (tipo 1):** en una caja, un dígito solo aparece en una fila o columna → se elimina del resto de esa fila o columna.
- **Claiming (tipo 2):** en una fila o columna, un dígito solo aparece dentro de una caja → se elimina del resto de la caja.

**Tests:** un tablero por tipo, en fila y en columna; caso sin eliminaciones (no debe devolver paso).

### 1.7 Subconjuntos desnudos
- Búsqueda genérica de N celdas con N candidatos en total en una unidad (N = 2, 3, 4); una sola implementación parametrizada por N.
- **Locked Pair/Triple:** subconjunto desnudo contenido a la vez en una línea y una caja; elimina en ambas unidades. HoDoKu lo clasifica como intersección y lo prueba antes que Locked Candidates; reutiliza la búsqueda de subconjuntos desnudos.

**Tests:** par, trío (incluido el caso en que ninguna celda tiene los 3 candidatos) y cuarteto; Locked Pair con eliminaciones en las dos unidades. Añadir el Naked Pair de la maqueta (columna 1, {1,3}) como caso de prueba.

### 1.8 Subconjuntos ocultos
N dígitos que, dentro de una unidad, solo aparecen en las mismas N celdas → se eliminan los demás candidatos de esas celdas. Implementación paralela a 1.7.

**Tests:** par, trío y cuarteto ocultos; caso en que el subconjunto oculto es también desnudo (no debe dar un paso vacío).

### 1.9 Orden de HoDoKu y configuración (D-13)
- `HintEngine.DEFAULT_TECHNIQUES` en el orden de `SolverConfig` anotado en 0.6, filtrado a las técnicas implementadas.
- El constructor ya acepta una lista; añadir un test que compruebe que un orden distinto cambia la técnica elegida.

### 1.10 Oráculo
- `tools/oracle/export_steps.py`: para cada puzzle del banco, el camino de resolución de HoDoKu con las técnicas de la Fase 1. En cada paso guarda la técnica, las colocaciones y eliminaciones, y **todas** las instancias de esa técnica disponibles en ese estado (`find_all_steps`).
- Salida versionada: `solver/src/test/resources/oracle/<grupo>.json`.
- Tabla de equivalencia entre los nombres de técnica de HoDoKu y `TechniqueId`.
- `OracleTest` (Kotlin, lee el JSON con `kotlinx-serialization`): recorre el camino aplicando los pasos de HoDoKu para seguir sincronizado y, en cada estado, comprueba que:
  1. nuestro `nextStep` tiene la misma técnica;
  2. nuestro paso está entre las instancias que HoDoKu encuentra (mismas colocaciones y eliminaciones).
- Los fallos se informan con `describe()` de ambos pasos y el estado del tablero.

**Hecho cuando:** `OracleTest` pasa sobre todo el banco.

### 1.11 reglib en local *(opcional)*
- `tools/oracle/fetch_reglib.py` descarga la suite de regresión de `hodoku-py` en `tools/oracle/reglib/` (ignorado por git; es material GPL) y la convierte a un formato simple: candidatos de partida, técnica y eliminaciones esperadas.
- `ReglibTest` carga esos casos para las técnicas implementadas y se **omite** si la carpeta no existe, así que la CI no la necesita.

### 1.12 Cierre de fase
- Actualizar `03-diseno.md` §3.2 (estado ✅) y las casillas de `04-plan-implementacion.md`.
- Revisar que D-09 puede pasar de *Provisional* a *Aceptada*.

**Criterio de la Fase 1** (de `04-plan-implementacion.md`): todos los puzzles del banco se resuelven con el motor, `OracleTest` y `StepConsistencyTest` pasan, y la CI está en verde.

---

## Orden de trabajo

```
0.1 JDK ─► 0.2 wrapper ─► 0.3 licencia ─► 0.4 commit + remoto ─► 0.5 CI
                                     └──► 0.6 oráculo (Python)
1.1 modelo ─► 1.2 backtracking ─► 1.3 banco ─► 1.4 propiedad
          ─► 1.5 Full House ─► 1.6 Locked Candidates ─► 1.7 desnudos ─► 1.8 ocultos
          ─► 1.9 orden ─► 1.10 oráculo ─► (1.11 reglib) ─► 1.12 cierre
```

Se propone **un commit por tarea**, con sus tests. A partir de 0.5, cada tarea de la Fase 1 en su propia rama con PR, para que la CI la valide antes de mezclar.

## Riesgos de estas fases

| Riesgo | Mitigación |
|---|---|
| El orden real de HoDoKu no coincide con el anotado, o depende de la configuración | Extraído de `SolverConfig` en 0.6; el `OracleTest` detecta cualquier desajuste |
| HoDoKu llama de otra forma a algunas técnicas o las divide de otra forma (Locked Candidates 1/2, Locked Pair) | Tabla de equivalencia explícita en 1.10 |
| `hodoku-py` (0.2.x) tiene errores o cambia su API | Versión fijada; los resultados versionados en JSON no dependen de reinstalarlo |
| Contaminación GPL | Nunca copiar código de HoDoKu o `hodoku-py`; reglib fuera del repositorio |
| Rendimiento del motor (cuartetos) | Medir solo si el banco tarda demasiado en los tests; con máscaras de bits debería bastar |
