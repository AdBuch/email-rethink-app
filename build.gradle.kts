import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version "2.1.21" apply false
    kotlin("android") version "2.1.21" apply false
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
}

// Einheitliches JVM-Ziel für Java und Kotlin in allen Modulen (Review W9).
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> { jvmToolchain(17) }
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> { jvmToolchain(17) }
    }
}
