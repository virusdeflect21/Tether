@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (and its nanojson dependency) are published on JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Tether"

include(":tether-mobile")
include(":tether-tv")
include(":core:model")
include(":core:database")
include(":core:lyrics")
include(":core:data")
include(":core:downloads")
include(":core:player")
include(":core:design")
