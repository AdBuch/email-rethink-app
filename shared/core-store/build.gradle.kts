plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            // Gemeinsames Framework für die iOS-App: Speicher + Transport-Typen.
            baseName = "Shared"
            // Statisch, damit die App SQLCipher (statt der System-SQLite) dazulinkt (ADR-007).
            isStatic = true
            export(project(":shared:core-transport"))
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-transport"))
            implementation("app.cash.sqldelight:runtime:2.0.2")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
            implementation("androidx.sqlite:sqlite:2.4.0")
        }
        iosMain.dependencies {
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
            }
        }
    }
}

android {
    namespace = "ch.emailrethink.store"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
}

sqldelight {
    databases {
        create("MailDatabase") {
            packageName.set("ch.emailrethink.store.db")
            // Upserts (ON CONFLICT … DO UPDATE) brauchen mindestens den Dialekt 3.24.
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.0.2")
        }
    }
    // Auf iOS wird SQLCipher von der App gelinkt, nicht die unverschlüsselte System-SQLite.
    linkSqlite.set(false)
}
