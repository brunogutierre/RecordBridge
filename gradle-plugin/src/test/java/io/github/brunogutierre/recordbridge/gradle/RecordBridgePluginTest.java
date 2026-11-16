package io.github.brunogutierre.recordbridge.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the plugin in a real Gradle build of a small Spring application. */
class RecordBridgePluginTest {

  private static final String CONTROLLER =
      """
      package demo;

      import org.springframework.web.bind.annotation.*;

      @RestController
      @RequestMapping("/api/hello")
      public class HelloController {
        public record Hello(String message, int count) {}

        @GetMapping("/{name}")
        public Hello hello(@PathVariable String name) {
          return new Hello("Hello " + name, 1);
        }
      }
      """;

  @TempDir Path project;

  @BeforeEach
  void setUp() throws IOException {
    write("settings.gradle.kts", "rootProject.name = \"demo\"\n");
    write("gradle.properties", "org.gradle.jvmargs=-Xmx512m\n");
    write("src/main/java/demo/HelloController.java", CONTROLLER);
  }

  private void write(String path, String content) throws IOException {
    Path file = project.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private void buildScript(String compilerArgs, String extension) throws IOException {
    write(
        "build.gradle.kts",
        """
        plugins {
            java
            id("io.github.brunogutierre.recordbridge")
        }

        repositories { mavenCentral() }

        dependencies { implementation("org.springframework:spring-web:7.0.10") }

        tasks.withType<JavaCompile>().configureEach { %s }

        recordBridge { %s }
        """
            .formatted(compilerArgs, extension));
  }

  private GradleRunner runner(String... arguments) {
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(arguments)
        .forwardOutput();
  }

  @Test
  void generatesTheApiLayerAndIsCacheable() throws IOException {
    buildScript(
        "options.compilerArgs.add(\"-parameters\")",
        "basePackages = listOf(\"demo\"); outputDir = layout.projectDirectory.dir(\"web/api\")");

    BuildResult first = runner("generateTypeScript", "--configuration-cache").build();

    assertThat(first.task(":generateTypeScript").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(first.getOutput()).contains("RecordBridge: 1 controllers");
    assertThat(project.resolve("web/api/hello.api.ts"))
        .content()
        .contains("hello(name: string): Observable<Hello>");
    assertThat(project.resolve("web/api/models.ts")).content().contains("count: number;");

    BuildResult second = runner("generateTypeScript", "--configuration-cache").build();

    assertThat(second.task(":generateTypeScript").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(second.getOutput()).contains("Reusing configuration cache");
  }

  @Test
  void explainsMissingParameterNames() throws IOException {
    buildScript("", "");

    BuildResult result = runner("generateTypeScript").buildAndFail();

    assertThat(result.getOutput()).contains("compile with the -parameters flag");
  }

  @Test
  void validatesTheNullnessOption() throws IOException {
    buildScript("options.compilerArgs.add(\"-parameters\")", "unmarkedNullness = \"sometimes\"");

    BuildResult result = runner("generateTypeScript").buildAndFail();

    assertThat(result.getOutput()).contains("must be NON_NULL or NULLABLE, not SOMETIMES");
  }
}
