package io.github.brunogutierre.recordbridge.maven;

import io.github.brunogutierre.recordbridge.generator.Generator;
import io.github.brunogutierre.recordbridge.generator.GeneratorConfig;
import io.github.brunogutierre.recordbridge.generator.GeneratorResult;
import io.github.brunogutierre.recordbridge.reader.UnmarkedNullness;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Reads the compiled controllers and writes the Angular API layer. Bound to {@code
 * process-classes}; its parameters are declared in {@code META-INF/maven/plugin.xml}, and Maven
 * injects them into these fields by name.
 */
public class GenerateMojo extends AbstractMojo {

  private @Nullable MavenProject project;
  private @Nullable File classesDirectory;
  private @Nullable File outputDirectory;
  private @Nullable List<String> basePackages;
  private String unmarkedNullness = "NON_NULL";
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("RecordBridge: skipped");
      return;
    }
    MavenProject currentProject = require(project, "project");
    var config =
        new GeneratorConfig(
            basePackages == null ? List.of() : basePackages,
            require(outputDirectory, "outputDirectory").toPath(),
            unmarkedNullness());
    // Parent-first delegation to the plugin's class realm: Spring and Jackson resolve to the
    // classes RecordBridge was built with, so the annotations on the application classes match.
    try (var loader = new URLClassLoader(urls(currentProject), getClass().getClassLoader())) {
      GeneratorResult result =
          Generator.generate(
              config, List.of(require(classesDirectory, "classesDirectory").toPath()), loader);
      result.skippedClasses().forEach(c -> getLog().warn("RecordBridge skipped " + c));
      getLog()
          .info(
              "RecordBridge: "
                  + result.controllers()
                  + " controllers, "
                  + result.types()
                  + " types → "
                  + config.outputDir());
    } catch (IOException | DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("RecordBridge: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      throw new MojoExecutionException("RecordBridge: " + e.getMessage(), e);
    }
  }

  private UnmarkedNullness unmarkedNullness() throws MojoExecutionException {
    String value = unmarkedNullness.toUpperCase(Locale.ROOT);
    try {
      return UnmarkedNullness.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(
          "unmarkedNullness must be NON_NULL or NULLABLE, not " + value, e);
    }
  }

  private static URL[] urls(MavenProject project)
      throws DependencyResolutionRequiredException, MalformedURLException {
    var urls = new ArrayList<URL>();
    for (String element : project.getRuntimeClasspathElements()) {
      urls.add(new File(element).toURI().toURL());
    }
    return urls.toArray(URL[]::new);
  }

  private static <T> T require(@Nullable T value, String name) throws MojoExecutionException {
    if (value == null) {
      throw new MojoExecutionException("RecordBridge: " + name + " is not set");
    }
    return value;
  }
}
