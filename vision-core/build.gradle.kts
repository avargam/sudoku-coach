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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.bytedeco:opencv:$bytedecoOpenCv")
    testRuntimeOnly("org.bytedeco:opencv:$bytedecoOpenCv:$bytedecoNatives")
    testRuntimeOnly("org.bytedeco:openblas:0.3.34-1.5.14:$bytedecoNatives")
    testRuntimeOnly("org.bytedeco:javacpp:1.5.14:$bytedecoNatives")
}

tasks.test {
    useJUnitPlatform()
    // Las fotos y etiquetas de prueba viven fuera del módulo (D-17).
    systemProperty("sudokucoach.testdata", rootProject.file("testdata/vision").absolutePath)
}
