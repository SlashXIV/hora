plugins {
    id("hora.android.library")
    id("hora.android.hilt")
}

android {
    namespace = "com.gabrielifrim.hora.core.data"
}

// Le schéma de la base est exporté en JSON et versionné : c'est ce qui rend les
// migrations vérifiables, la base locale étant la seule copie de l'agenda.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

androidComponents {
    onVariants { variant ->
        // Les schémas exportés servent d'assets aux tests de migration.
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

dependencies {
    api(projects.core.domain)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
