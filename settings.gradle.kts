pluginManagement {
    // Le build composite build-logic fournit les plugins de convention « hora.* ».
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Aucun module ne déclare ses propres dépôts : tout passe par ici.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hora"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:reminders")
include(":core:ics")

include(":feature:today")
include(":feature:calendar")
include(":feature:event")
include(":feature:settings")
include(":feature:widget")
