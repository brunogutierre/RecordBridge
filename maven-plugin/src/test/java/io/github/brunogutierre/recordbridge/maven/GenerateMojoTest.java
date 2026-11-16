package io.github.brunogutierre.recordbridge.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the plugin in a real Maven build of a small Spring application. The build publishes every
 * RecordBridge module to a file repository and unpacks a Maven distribution before this test.
 */
class GenerateMojoTest {

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
    Path source = project.resolve("src/main/java/demo/HelloController.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, CONTROLLER);
  }

  private void pom(String configuration) throws IOException {
    Files.writeString(
        project.resolve("pom.xml"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>demo</groupId>
          <artifactId>demo</artifactId>
          <version>1</version>
          <properties>
            <maven.compiler.release>17</maven.compiler.release>
            <maven.compiler.parameters>true</maven.compiler.parameters>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
          </properties>
          <dependencies>
            <dependency>
              <groupId>org.springframework</groupId>
              <artifactId>spring-web</artifactId>
              <version>7.0.10</version>
            </dependency>
          </dependencies>
          <pluginRepositories>
            <pluginRepository>
              <id>recordbridge-test</id>
              <url>%s</url>
            </pluginRepository>
          </pluginRepositories>
          <build>
            <plugins>
              <plugin>
                <groupId>io.github.brunogutierre.recordbridge</groupId>
                <artifactId>recordbridge-maven-plugin</artifactId>
                <version>%s</version>
                <executions>
                  <execution>
                    <goals><goal>generate</goal></goals>
                  </execution>
                </executions>
                <configuration>%s</configuration>
              </plugin>
            </plugins>
          </build>
        </project>
        """
            .formatted(
                System.getProperty("recordbridge.test.repo"),
                System.getProperty("recordbridge.version"),
                configuration));
  }

  private record Run(int exitCode, String output) {}

  private Run maven(String... goals) throws Exception {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("recordbridge.maven.home"), "bin", "mvn").toString());
    command.addAll(
        List.of(
            "-B",
            "-U", // the plugin is a snapshot that changes on every build
            "-Dmaven.repo.local=" + System.getProperty("recordbridge.local.repo")));
    command.addAll(List.of(goals));
    var builder = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    builder.environment().put("MAVEN_OPTS", "-Xmx512m");
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes());
    process.waitFor(10, TimeUnit.MINUTES);
    return new Run(process.exitValue(), output);
  }

  @Test
  void generatesTheApiLayerDuringTheBuild() throws Exception {
    pom(
        "<basePackages><basePackage>demo</basePackage></basePackages>"
            + "<outputDirectory>${project.basedir}/web/api</outputDirectory>");

    Run run = maven("process-classes");

    assertThat(run.exitCode()).as(run.output()).isZero();
    assertThat(run.output()).contains("RecordBridge: 1 controllers");
    assertThat(project.resolve("web/api/hello.api.ts"))
        .content()
        .contains("hello(name: string): Observable<Hello>");
  }

  @Test
  void validatesTheNullnessOption() throws Exception {
    pom("<unmarkedNullness>sometimes</unmarkedNullness>");

    Run run = maven("process-classes");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.output())
        .contains("unmarkedNullness must be NON_NULL or NULLABLE, not SOMETIMES");
  }

  @Test
  void canBeSkipped() throws Exception {
    pom("");

    Run run = maven("process-classes", "-Drecordbridge.skip=true");

    assertThat(run.exitCode()).as(run.output()).isZero();
    assertThat(run.output()).contains("RecordBridge: skipped");
    assertThat(project.resolve("target/recordbridge")).doesNotExist();
  }
}
