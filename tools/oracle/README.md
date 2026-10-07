# Oráculo HoDoKu

Se usa [`hodoku-py`](https://pypi.org/project/hodoku-py/) (port en Python de HoDoKu 2.2.0, con generador, `solve`, `get_hint`, `find_all_steps` y `rate`) para contrastar el `solver` con HoDoKu y generar el banco de puzzles (decisión D-09).

HoDoKu y `hodoku-py` son GPL-3.0 y este repositorio es Apache-2.0: aquí solo se usan como herramienta externa. **No se copia su código** al proyecto, y su suite *reglib* se guarda únicamente en local (`reglib/`, ignorada por git).

## Preparación (Ubuntu / WSL)

```bash
cd tools/oracle
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python check.py            # resuelve un puzzle e imprime sus pasos
```

Requiere Python ≥ 3.11. La versión de `hodoku-py` está fijada en `requirements.txt`.

## Orden de técnicas de HoDoKu

Orden por defecto de `SolverConfig.solver_steps` en `hodoku-py` 0.2.1. El `HintEngine` lo sigue (D-13).

| # | `SolutionType` | `SolutionCategory` | Fase |
|---|---|---|---|
| 1 | `FULL_HOUSE` | SINGLES | 1 |
| 2 | `NAKED_SINGLE` | SINGLES | 1 |
| 3 | `HIDDEN_SINGLE` | SINGLES | 1 |
| 4 | `LOCKED_PAIR` | INTERSECTIONS | 1 |
| 5 | `LOCKED_TRIPLE` | INTERSECTIONS | 1 |
| 6 | `LOCKED_CANDIDATES_1` (Pointing) | INTERSECTIONS | 1 |
| 7 | `LOCKED_CANDIDATES_2` (Claiming) | INTERSECTIONS | 1 |
| 8 | `NAKED_PAIR` | SUBSETS | 1 |
| 9 | `NAKED_TRIPLE` | SUBSETS | 1 |
| 10 | `HIDDEN_PAIR` | SUBSETS | 1 |
| 11 | `HIDDEN_TRIPLE` | SUBSETS | 1 |
| 12 | `NAKED_QUADRUPLE` | SUBSETS | 1 |
| 13 | `HIDDEN_QUADRUPLE` | SUBSETS | 1 |
| 14 | `X_WING` | BASIC_FISH | 6 |
| 15 | `SWORDFISH` | BASIC_FISH | 6 |
| 16 | `JELLYFISH` | BASIC_FISH | 6 |
| 17 | `REMOTE_PAIR` | CHAINS_AND_LOOPS | 6 |
| 18 | `BUG_PLUS_1` | UNIQUENESS | 6 |
| 19 | `SKYSCRAPER` | SINGLE_DIGIT_PATTERNS | 6 |
| 20 | `TWO_STRING_KITE` | SINGLE_DIGIT_PATTERNS | 6 |
| 21 | `TURBOT_FISH` | SINGLE_DIGIT_PATTERNS | 6 |
| 22 | `EMPTY_RECTANGLE` | SINGLE_DIGIT_PATTERNS | 6 |
| 23 | `W_WING` | WINGS | 6 |
| 24 | `XY_WING` | WINGS | 6 |
| 25 | `XYZ_WING` | WINGS | 6 |
| 26… | `UNIQUENESS_1`…`6`, `HIDDEN_RECTANGLE`, `AVOIDABLE_RECTANGLE_1/2`, fish con aletas, cadenas… | | 6 |

Para regenerar la lista completa:

```bash
python -c "from hodoku.config import DEFAULT_CONFIG as C; [print(s.solution_type.name, s.category.name) for s in C.solver_steps]"
```

## Banco de puzzles

`generate_bank.py` genera el banco → `solver/src/test/resources/bank/` (versionado). Con los valores por defecto (100 por grupo, semilla 2026) tarda ~1,5 min en 12 núcleos y es reproducible: cada puzzle usa su propia semilla.

```bash
python generate_bank.py --per-group 100 --seed 2026
```

Resultado actual: 5749 intentos, 1130 no resolubles solo con la Fase 1. Puzzles del banco que usan cada técnica:

| Técnica | Puzzles | | Técnica | Puzzles |
|---|---|---|---|---|
| FULL_HOUSE | 300 | | NAKED_PAIR | 71 |
| NAKED_SINGLE | 300 | | NAKED_TRIPLE | 27 |
| HIDDEN_SINGLE | 284 | | HIDDEN_PAIR | 9 |
| LOCKED_PAIR | 48 | | HIDDEN_TRIPLE | 2 |
| LOCKED_TRIPLE | 7 | | NAKED_QUADRUPLE | 1 |
| LOCKED_CANDIDATES_1 | 160 | | HIDDEN_QUADRUPLE | 0 |
| LOCKED_CANDIDATES_2 | 54 | | | |

Las técnicas raras (tríos ocultos y cuartetos) no se fuerzan en el banco: se cubren con tableros construidos en sus tests unitarios y con reglib.

## Scripts previstos (Fase 1)

- `export_steps.py`: exporta los pasos de HoDoKu → `solver/src/test/resources/oracle/` (versionado; la CI no necesita Python).
- `fetch_reglib.py`: descarga *reglib* en `reglib/` (solo local).
