plugins {
    id("hora.android.library")
    id("hora.android.compose")
}

android {
    namespace = "com.gabrielifrim.hora.feature.today"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
