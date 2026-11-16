plugins {
    id("recordbridge.java-library")
}

dependencies {
    api(project(":model"))
}

dependencies {
    implementation(libs.jackson.databind)
}
