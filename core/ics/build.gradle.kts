// Module JVM pur : lecture et écriture iCalendar (RFC 5545), testables sans émulateur.
plugins {
    id("hora.jvm.library")
}

dependencies {
    api(projects.core.domain)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
