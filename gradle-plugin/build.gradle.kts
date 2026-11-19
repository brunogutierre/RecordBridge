plugins {
    id("recordbridge.java-library")
    `java-gradle-plugin`
}

description = "Gradle plugin that generates TypeScript types and Angular services from a Spring Boot API."

dependencies {
    implementation(project(":generator"))
}

gradlePlugin {
    plugins {
        create("recordBridge") {
            id = "io.github.brunogutierre.recordbridge"
            implementationClass = "io.github.brunogutierre.recordbridge.gradle.RecordBridgePlugin"
            displayName = "RecordBridge"
            description =
                "Generates TypeScript types and Angular services from Spring Boot controllers and Java records."
        }
    }
}

tasks.test {
    // TestKit builds download Spring from Maven Central and start their own daemon.
    maxParallelForks = 1
}
