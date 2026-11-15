plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("app.cash.sqldelight")
}
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation("app.cash.sqldelight:runtime:2.0.2") }
        androidMain.dependencies { implementation("app.cash.sqldelight:android-driver:2.0.2") }
        iosMain.dependencies { implementation("app.cash.sqldelight:native-driver:2.0.2") }
    }
}
android { namespace = "ch.emailrethink.store"; compileSdk = 35; defaultConfig { minSdk = 29 } }
sqldelight { databases { create("MailDatabase") { packageName.set("ch.emailrethink.store.db") } } }
