plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("com.android.library") version "9.4.1" apply false
}

allprojects {
    group = "dev.sudokucoach"
    version = "0.1.0-SNAPSHOT"
}
