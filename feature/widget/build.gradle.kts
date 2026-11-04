plugins {
    id("hora.android.library")
    id("hora.android.compose")
    id("hora.android.hilt")
}

android {
    namespace = "com.gabrielifrim.hora.feature.widget"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.reminders)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
