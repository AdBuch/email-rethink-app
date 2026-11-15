plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ch.emailrethink.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "ch.emailrethink.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Nur die ABIs, für die der Rust-Kern gebaut wird.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    packaging {
        // JNA und SQLCipher bringen ihre nativen Bibliotheken unkomprimiert mit.
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(project(":shared:core-store"))
    implementation(project(":shared:core-transport"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.7.8")
}
