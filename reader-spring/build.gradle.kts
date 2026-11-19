plugins {
    id("recordbridge.java-library")
    id("recordbridge.published")
}

description = "Reads Spring MVC controllers and Jackson 3 types into the RecordBridge model."

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
