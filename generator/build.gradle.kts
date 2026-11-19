plugins {
    id("recordbridge.java-library")
    id("recordbridge.published")
}

description = "Scans compiled classes and writes the Angular API layer; used by the build plugins."

dependencies {
    api(project(":reader-spring"))
    api(project(":emitter-ts"))
    implementation(libs.jackson.databind)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.context)
}
