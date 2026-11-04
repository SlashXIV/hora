plugins {
    id("hora.android.application")
    id("hora.android.compose")
    id("hora.android.hilt")
    // Routes de navigation typées : Navigation Compose sérialise les objets de route.
    alias(libs.plugins.kotlin.serialization)
}

// Version publiée : la SEULE ligne à modifier lors d'une release.
// Règle : correctif = patch, nouveau jalon = mineure, 1.0.0 = cahier des charges complet.
val horaVersion = "0.7.0"

android {
    namespace = "com.gabrielifrim.hora"

    defaultConfig {
        applicationId = "com.gabrielifrim.hora"
        val (major, minor, patch) = horaVersion.split(".").map { it.toInt() }
        versionCode = major * 10000 + minor * 100 + patch
        versionName = horaVersion
    }

    // Signature de publication : jamais dans le dépôt. En local, propriétés Gradle
    // hora.signing.* (~/.gradle/gradle.properties) ; en CI, variables d'environnement.
    // Absentes, le release reste non signé : un clone du dépôt se construit toujours.
    fun signing(key: String): String? =
        providers.gradleProperty("hora.signing.$key").orNull
            ?: providers.environmentVariable("HORA_SIGNING_${key.uppercase()}").orNull
    val signingValues = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { signing(it) }
    val canSign = signingValues.values.all { it != null }
    if (canSign) {
        signingConfigs.create("release") {
            storeFile = file(signingValues.getValue("storeFile")!!)
            storePassword = signingValues.getValue("storePassword")
            keyAlias = signingValues.getValue("keyAlias")
            keyPassword = signingValues.getValue("keyPassword")
        }
    }

    buildTypes {
        release {
            if (canSign) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.reminders)
    implementation(projects.feature.today)
    implementation(projects.feature.calendar)
    implementation(projects.feature.event)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
