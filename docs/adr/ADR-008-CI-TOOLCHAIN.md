# ADR-008: Reproduzierbare Toolchain und GitHub Actions für Android und iOS

- **Status:** Angenommen (CI-Plattform und Prinzipien); exakte Versionsmatrix nach Build-Spike festzulegen
- **Datum:** 2026-11-15
- **Entscheidung:** Offener Punkt 16
- **Bezug:** ADR-002, ADR-006, ADR-007

## Kontext

Die App besteht aus Kotlin Multiplatform, Android Jetpack Compose, iOS SwiftUI und einem Rust-IMAP-Kern mit UniFFI. Der aktuelle Phase-0-Branch enthält ein noch nicht verifiziertes Gradle-Grundgerüst. Builds und Tests dürfen nicht nur von lokalen Entwicklerrechnern abhängen.

## Entscheidung

1. **GitHub Actions** ist die CI-Plattform.
2. **Linux-Runner** prüfen Gradle/Kotlin/KMP, Android-Build, Rust-Lint/Unit-Tests und GreenMail-Docker-Integrationstests.
3. **macOS-Runner** prüfen iOS-/Swift-/Xcode-Build, KMP-iOS-Framework, Rust-iOS-Artefakte und UniFFI-Swift-Bindings.
4. Gradle Wrapper und dessen Prüfsumme, Java-Version, Kotlin-, AGP-, Compose-, SQLDelight-, Rust-/Cargo-, UniFFI- und Xcode-Versionen werden reproduzierbar gepinnt. `Cargo.lock` wird eingecheckt.
5. CI läuft bei Pull Requests und Änderungen am Hauptbranch. Schutzregeln sollen Merge nur bei erfolgreichen erforderlichen Checks erlauben.
6. Integrationstests verwenden GreenMail und Testzertifikate; keine produktiven Konten oder Zugangsdaten in CI.
7. Dependency-Caches dürfen keine Secrets enthalten; GitHub-Actions-Versionen sollen auf geprüfte Commit-SHAs gepinnt werden. Für Release-Signing sind separate, geschützte Secrets/Environments erforderlich.
8. Die Versionsmatrix wird erst als **verifiziert** bezeichnet, wenn ein vollständiger Android- und iOS-Build auf den jeweiligen Runnern erfolgreich war. Aktuelle Versionen in `build.gradle.kts` sind vorläufig und gelten nicht als getestet.

## Vorgesehene CI-Jobs

| Job | Runner | Kriterien |
|---|---|---|
| `rust-check` | Ubuntu | fmt, clippy, cargo test, Abhängigkeitsprüfung |
| `android-build` | Ubuntu | Gradle Wrapper, KMP-Compile, Android Debug APK, Unit-Tests |
| `imap-integration` | Ubuntu | GreenMail per Docker, TLS-/IMAP-/Fehlerfälle |
| `ios-build` | macOS | Xcode, iOS Simulator, SwiftUI, KMP + UniFFI/XCFramework |
| `security-check` | Ubuntu | Dependency-/Lizenzprüfung, keine Klartext-Secrets |

## Abnahmekriterien

- Vollständiger Android- und iOS-Build aus frischem Checkout ohne manuelle Schritte.
- Verifizierte Versionsmatrix dokumentiert und in Konfigurationsdateien festgehalten.
- Fehlerhafte Tests und Builds blockieren PR-Merge.
- Kein Anspruch auf erfolgreiche CI vor tatsächlich ausgeführten Workflows.

## Konsequenzen

GitHub Actions ermöglicht einheitliche Qualitätskontrollen. macOS-Runner und Rust-/KMP-/Swift-Integration erhöhen Kosten und Pflegeaufwand; Builds bleiben dennoch reproduzierbar und plattformübergreifend überprüfbar.
