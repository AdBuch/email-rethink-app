plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ch.emailrethink.android"
    compileSdk = 35
    defaultConfig { applicationId = "ch.emailrethink.android"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":shared:core-store"))
    implementation(project(":shared:core-transport"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.7.8")
}
