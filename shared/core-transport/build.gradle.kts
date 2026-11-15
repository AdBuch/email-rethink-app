plugins { kotlin("multiplatform"); id("com.android.library") }

val rustDir = rootProject.layout.projectDirectory.dir("rust/mail-transport")
val scriptsDir = rootProject.layout.projectDirectory.dir("scripts")
val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs")
val uniffiKotlinDir = layout.buildDirectory.dir("generated/uniffi/kotlin")

fun Exec.rustInputs() {
    inputs.dir(rustDir.dir("src"))
    inputs.files(rustDir.file("Cargo.toml"), rustDir.file("Cargo.lock"), rustDir.file("uniffi.toml"))
}

// Rust-Kern für Android bauen (ADR-006). Benötigt cargo-ndk und das Android NDK.
val buildRustAndroid by tasks.registering(Exec::class) {
    group = "rust"
    description = "Baut libmail_transport.so für arm64-v8a und x86_64."
    rustInputs()
    inputs.file(scriptsDir.file("build-rust-android.sh"))
    outputs.dir(rustJniLibsDir)
    commandLine(scriptsDir.file("build-rust-android.sh").asFile.absolutePath, rustJniLibsDir.get().asFile.absolutePath)
}

// Kotlin-Bindings aus dem Rust-Kern erzeugen (UniFFI).
val generateUniffiKotlin by tasks.registering(Exec::class) {
    group = "rust"
    description = "Erzeugt die UniFFI-Kotlin-Bindings."
    rustInputs()
    inputs.file(scriptsDir.file("generate-bindings.sh"))
    outputs.dir(uniffiKotlinDir)
    commandLine(scriptsDir.file("generate-bindings.sh").asFile.absolutePath, "kotlin", uniffiKotlinDir.get().asFile.absolutePath)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        androidMain {
            kotlin.srcDir(generateUniffiKotlin)
            dependencies {
                implementation("net.java.dev.jna:jna:5.17.0@aar")
            }
        }
    }
}

android {
    namespace = "ch.emailrethink.transport"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    sourceSets["main"].jniLibs.srcDir(rustJniLibsDir)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(buildRustAndroid) }
tasks.named("preBuild") { dependsOn(buildRustAndroid, generateUniffiKotlin) }
