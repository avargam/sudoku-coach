# ML Kit (reconocimiento de texto). Con R8 en modo completo, el de AGP 9, la release fallaba al crear
# el reconocedor: TextRecognition.getClient daba un NullPointerException porque R8 alteraba las clases
# internas con las que ML Kit registra sus componentes (tarea P.1). Sus propias reglas no bastan.
-keep class com.google.mlkit.** { *; }
