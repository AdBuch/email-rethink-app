pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "email-rethink-app"
include(":shared:core-transport", ":shared:core-mime", ":shared:core-store", ":shared:core-classify", ":shared:core-extract", ":shared:core-crypto", ":androidApp")
