import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Se compila con JDK 21, pero se genera bytecode JVM 17 para que Android pueda consumir el módulo (D-15).
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

dependencies {
    testImplementation(kotlin("test"))
    // Solo para leer en los tests los pasos exportados de HoDoKu (tarea 1.10); el módulo principal no depende de ella.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
