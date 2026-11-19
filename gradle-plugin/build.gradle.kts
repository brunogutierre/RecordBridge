plugins {
    id("recordbridge.java-library")
    alias(libs.plugins.plugin.publish)
}

description = "Gradle plugin that generates TypeScript types and Angular services from a Spring Boot API."

dependencies {
    implementation(project(":generator"))
}

gradlePlugin {
    website = "https://github.com/brunogutierre/RecordBridge"
    vcsUrl = "https://github.com/brunogutierre/RecordBridge"
    plugins {
        create("recordBridge") {
            id = "io.github.brunogutierre.recordbridge"
            implementationClass = "io.github.brunogutierre.recordbridge.gradle.RecordBridgePlugin"
            displayName = "RecordBridge"
            description =
                "Generates TypeScript types and Angular services from Spring Boot controllers and Java records."
            tags = listOf("typescript", "angular", "spring-boot", "code-generation", "jackson")
        }
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    // The Plugin Portal hosts the plugin jar; its dependencies come from Maven Central.
    if (name == "pluginMaven") {
        artifactId = "recordbridge-gradle-plugin"
    }
}

tasks.test {
    // TestKit builds download Spring from Maven Central and start their own daemon.
    maxParallelForks = 1
}
