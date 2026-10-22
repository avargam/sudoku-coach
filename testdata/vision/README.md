# Conjunto de fotos de prueba (D-17)

Fotos propias de sudokus en papel para medir la visión (Fase 2). Los tests de `vision-core` las leen
desde aquí; ver `docs/06-plan-fase-2.md`.

```
testdata/vision/
├── raw/            originales (ignorado por git)
├── photos/NNN.jpg  copia versionada: lado mayor 1600 px, JPEG, sin EXIF
├── labels/NNN.txt  etiqueta de cada foto
└── results/        predicciones de ML Kit en el móvil (tarea 2.8), ver vision/README.md
```

Las fotos versionadas **no llevan EXIF** (puede incluir la ubicación) porque el repositorio será público.

## Formato de etiqueta

Una línea de metadatos, 9 líneas con los dígitos (`.` = vacía) y 9 líneas con el origen de cada celda
(`P` impreso, `M` manuscrito, `.` vacía). Las líneas en blanco se ignoran.

```
# luz=natural angulo=moderado papel=periodico notas=sombra_en_esquina
2..3..8..
.7...9.6.
...            (9 líneas de dígitos)
P..P..P..
.M...P.P.
...            (9 líneas de origen)
```

Metadatos previstos: `luz` (natural, artificial, sombra), `angulo` (frontal, moderado, fuerte),
`papel` (periodico, libro, impreso…) y `notas` libres, sin espacios.
