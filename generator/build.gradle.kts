plugins {
    id("recordbridge.java-library")
    id("recordbridge.published")
}

dependencies {
    api(project(":reader-spring"))
    api(project(":emitter-ts"))
    implementation(libs.jackson.databind)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.context)
}
