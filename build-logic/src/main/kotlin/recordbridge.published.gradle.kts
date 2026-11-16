// Publishes a library module as io.github.brunogutierre.recordbridge:recordbridge-<name>.
// For now only to a repository inside the build, which the Maven plugin tests resolve from;
// the release adds signing and Maven Central.
plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "recordbridge-${project.name}"
        }
    }
    repositories {
        maven {
            name = "test"
            url = uri(rootProject.layout.buildDirectory.dir("test-repo"))
        }
    }
}
