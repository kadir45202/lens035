import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: the colour science, film recipes, edit model and maths that do not
// depend on Android. Keeping it framework-free lets it be unit tested on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
