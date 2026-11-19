// Publishes a library module as io.github.brunogutierre.recordbridge:recordbridge-<name>.
// Two repositories inside the build: "test", which the Maven plugin tests resolve from, and
// "staging", which JReleaser signs and uploads to Maven Central on a release.
plugins {
    `java-library`
    `maven-publish`
}

java {
    // Maven Central requires both jars next to every library jar.
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        // Check the HTML and references, but don't ask for a comment on every member.
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addBooleanOption("Werror", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "recordbridge-${project.name}"
            pom {
                name = "RecordBridge ${project.name}"
                description = provider { project.description }
                url = "https://github.com/brunogutierre/RecordBridge"
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "brunogutierre"
                        name = "Bruno Gutierre"
                        url = "https://github.com/brunogutierre"
                    }
                }
                scm {
                    url = "https://github.com/brunogutierre/RecordBridge"
                    connection = "scm:git:https://github.com/brunogutierre/RecordBridge.git"
                    developerConnection = "scm:git:ssh://git@github.com/brunogutierre/RecordBridge.git"
                }
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/brunogutierre/RecordBridge/issues"
                }
            }
        }
    }
    repositories {
        maven {
            name = "test"
            url = uri(rootProject.layout.buildDirectory.dir("test-repo"))
        }
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}
