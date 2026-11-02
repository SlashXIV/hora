plugins {
    id("hora.android.library")
    id("hora.android.hilt")
}

android {
    namespace = "com.gabrielifrim.hora.core.reminders"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
