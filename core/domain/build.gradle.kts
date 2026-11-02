// Module JVM pur : aucune dépendance au framework Android.
// Toute la logique (récurrence, rappels, fêtes, saisie rapide…) vit ici, testable sans émulateur.
plugins {
    id("hora.jvm.library")
}

dependencies {
    // api : Flow et @Inject apparaissent dans la signature publique des interfaces.
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    implementation(libs.lib.recur)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
