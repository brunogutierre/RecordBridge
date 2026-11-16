plugins {
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
