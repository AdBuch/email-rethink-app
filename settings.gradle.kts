pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "email-rethink-app"
include(":shared:core-transport", ":shared:core-mime", ":shared:core-store", ":shared:core-classify", ":shared:core-extract", ":shared:core-crypto", ":androidApp")
