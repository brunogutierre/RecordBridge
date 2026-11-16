plugins {
    id("recordbridge.java-library")
    id("recordbridge.published")
}

val mavenDistribution by configurations.creating

dependencies {
    implementation(project(":generator"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    mavenDistribution("org.apache.maven:apache-maven:${libs.versions.maven.get()}:bin@zip")
}

val pluginVersion = project.version.toString()

tasks.processResources {
    // A local copy: lambdas that capture script properties break the configuration cache.
    val tokens = mapOf("version" to pluginVersion)
    inputs.property("version", pluginVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to tokens)
    }
}

publishing.publications.named<MavenPublication>("maven") {
    pom { packaging = "maven-plugin" }
}

// The functional test runs a real Maven build: unpack a Maven distribution and publish every
// RecordBridge module to the build's test repository first.
interface Archives {
    @get:Inject val operations: ArchiveOperations
}

val unpackMaven by tasks.registering(Sync::class) {
    // Local copies: lambdas that capture script objects break the configuration cache.
    val archives = objects.newInstance<Archives>().operations
    val distribution = mavenDistribution
    from(distribution.elements.map { files -> files.map { archives.zipTree(it) } })
    into(layout.buildDirectory.dir("maven"))
}

tasks.test {
    dependsOn(unpackMaven)
    listOf(":model", ":reader-spring", ":emitter-ts", ":generator", ":maven-plugin").forEach {
        dependsOn("$it:publishAllPublicationsToTestRepository")
    }
    val mavenHome = layout.buildDirectory.dir("maven/apache-maven-${libs.versions.maven.get()}")
    val testRepo = rootProject.layout.buildDirectory.dir("test-repo")
    val localRepo = rootProject.layout.buildDirectory.dir("maven-local-repo")
    systemProperty("recordbridge.maven.home", mavenHome.get().asFile.absolutePath)
    systemProperty(
        "recordbridge.test.repo",
        testRepo
            .get()
            .asFile
            .toURI()
            .toString(),
    )
    systemProperty("recordbridge.local.repo", localRepo.get().asFile.absolutePath)
    systemProperty("recordbridge.version", pluginVersion)
}
