plugins {
    id("recordbridge.java-library")
    id("recordbridge.published")
}

dependencies {
    api(project(":model"))
}

tasks.test {
    // Golden files: run with -PupdateGolden to rewrite them from the current output.
    systemProperty(
        "recordbridge.golden.dir",
        layout.projectDirectory
            .dir("src/test/resources/golden")
            .asFile.absolutePath,
    )
    systemProperty("recordbridge.golden.update", providers.gradleProperty("updateGolden").isPresent)
}
