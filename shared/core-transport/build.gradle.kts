plugins { kotlin("multiplatform"); id("com.android.library") }
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
}
android { namespace = "ch.emailrethink.transport"; compileSdk = 35; defaultConfig { minSdk = 29 } }
