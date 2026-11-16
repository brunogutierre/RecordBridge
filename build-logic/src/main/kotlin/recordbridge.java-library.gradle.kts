// Convention plugin shared by every RecordBridge library module.
plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

java {
    // Build with the latest JDK, but emit bytecode that Java 17 runtimes
    // (the Spring Boot 4 baseline) can load.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

base {
    archivesName = "recordbridge-${project.name}"
}

dependencies {
    "api"(libs.findLibrary("jspecify").get())
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("assertj-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports.xml.required = true
}

spotless {
    java {
        googleJavaFormat()
        formatAnnotations()
        removeUnusedImports()
    }
}
