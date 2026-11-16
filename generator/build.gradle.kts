plugins {
    id("recordbridge.java-library")
}

dependencies {
    api(project(":reader-spring"))
    api(project(":emitter-ts"))
    implementation(libs.jackson.databind)
    testImplementation(libs.spring.web)
}
