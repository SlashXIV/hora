plugins {
    id("hora.android.library")
    id("hora.android.compose")
    id("hora.android.hilt")
}

android {
    namespace = "com.gabrielifrim.hora.feature.event"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
