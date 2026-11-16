plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.github.brunogutierre.recordbridge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("io.projectreactor:reactor-core")
    implementation("org.jspecify:jspecify")
}

recordBridge {
    basePackages = listOf("example")
    outputDir = layout.projectDirectory.dir("web/src/app/api")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Serves the Angular build next to the API, on one origin.
    args("--server.port=8187", "--spring.web.resources.static-locations=file:web/dist/web/browser/")
}
