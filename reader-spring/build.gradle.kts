plugins {
    id("recordbridge.java-library")
}

dependencies {
    api(project(":model"))
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.spring.context)
    implementation(libs.spring.web)
    testImplementation(libs.reactor.core)
    testImplementation(libs.spring.webmvc)
}
