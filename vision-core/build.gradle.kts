import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

// Núcleo de visión en JVM pura (D-16): mismo bytecode JVM 17 que el solver, para que Android lo consuma.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

// OpenCV 4.14 en los dos lados (D-16). El código usa la API oficial org.opencv.*, que aquí aporta
// Bytedeco; en Android la aporta el AAR org.opencv:opencv, por eso es compileOnly.
val bytedecoOpenCv = "4.14.0-1.5.14"
val bytedecoNatives = "linux-x86_64" // solo los nativos de WSL y de la CI, no todas las plataformas

dependencies {
    compileOnly("org.bytedeco:opencv:$bytedecoOpenCv")

    testImplementation(kotlin("test"))
    // Solo en los tests: valida que las etiquetas de las fotos sean tableros posibles (tarea 2.5).
    testImplementation(project(":solver"))
    // Solo en los tests: lee las predicciones del móvil de testdata/vision/results (tarea 2.8).
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.bytedeco:opencv:$bytedecoOpenCv")
    testRuntimeOnly("org.bytedeco:opencv:$bytedecoOpenCv:$bytedecoNatives")
    testRuntimeOnly("org.bytedeco:openblas:0.3.34-1.5.14:$bytedecoNatives")
    testRuntimeOnly("org.bytedeco:javacpp:1.5.14:$bytedecoNatives")
}

val testDataDir = rootProject.file("testdata/vision").absolutePath

tasks.test {
    useJUnitPlatform()
    // Las fotos y etiquetas de prueba viven fuera del módulo (D-17). Se declaran como entrada para que
    // Gradle vuelva a ejecutar los tests al cambiar una foto o una etiqueta.
    systemProperty("sudokucoach.testdata", testDataDir)
    inputs.files(fileTree(testDataDir) { exclude("raw/**") }).withPropertyName("testdata")
}

// Etiquetado asistido (tarea 2.5): ./gradlew :vision-core:labelHelper [--args="003 004"]
tasks.register<JavaExec>("labelHelper") {
    group = "vision"
    description = "Genera plantillas de etiqueta e imágenes de revisión para las fotos de testdata/vision."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.sudokucoach.vision.core.LabelHelperKt"
    systemProperty("sudokucoach.testdata", testDataDir)
}
