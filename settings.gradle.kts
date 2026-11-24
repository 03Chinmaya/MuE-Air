rootProject.name = "MuE-Air"

pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}

include(":app")

include(":core:model")
include(":core:providerchain")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:design")
include(":core:player")
include(":core:providers")
include(":core:lyrics")
include(":core:auth")
include(":core:artist")
include(":core:testing")

include(":feature:auth")
include(":feature:home")
include(":feature:search")
include(":feature:player")
include(":feature:library")
include(":feature:listen")
include(":feature:artist")
include(":feature:settings")
