pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "MeritScreen"

include(":app")

include(":core:common")
include(":core:ui")
include(":core:network")
include(":core:database")
include(":core:firebase")
include(":core:security")
include(":core:analytics")
include(":core:testing")

include(":features:onboarding")
include(":features:authentication")
include(":features:parent")
include(":features:child")
include(":features:children")
include(":features:launcher")
include(":features:applications")
include(":features:screen-time")
include(":features:settings")
include(":features:devices")
include(":features:notifications")
