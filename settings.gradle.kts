pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // Каталог `libs` из `gradle/libs.versions.toml` импортируется Gradle 9 автоматически —
    // явный `versionCatalogs { create("libs") { from(...) } }` приводит к «from called twice».
    // План §1.2 приводит его для полноты, но на текущей версии Gradle он лишний.
}

rootProject.name = "MoneyKeeper"
include(":app")
include(":core:database")
include(":core:domain")
include(":core:ui")
include(":feature:accounts")
include(":feature:transactions")
include(":feature:dashboard")
include(":feature:analytics")
include(":feature:forecast")
include(":feature:settings")
include(":feature:auth")
