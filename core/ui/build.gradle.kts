plugins {
    id("hora.android.library")
    id("hora.android.compose")
}

android {
    namespace = "com.gabrielifrim.hora.core.ui"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
