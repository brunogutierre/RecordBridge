package io.github.brunogutierre.recordbridge.gradle;

import io.github.brunogutierre.recordbridge.generator.Generator;
import io.github.brunogutierre.recordbridge.generator.GeneratorConfig;
import io.github.brunogutierre.recordbridge.generator.GeneratorResult;
import io.github.brunogutierre.recordbridge.reader.UnmarkedNullness;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/**
 * Runs the generator in a worker process on the project's Java toolchain, so classes compiled for a
 * newer Java than the Gradle daemon's can still be loaded.
 */
public abstract class GenerateWork implements WorkAction<GenerateWork.Parameters> {

  private static final Logger LOGGER = Logging.getLogger(GenerateWork.class);

  /** What the worker needs, all serializable across processes. */
  public interface Parameters extends WorkParameters {
    ConfigurableFileCollection getClassesDirs();

    ConfigurableFileCollection getRuntimeClasspath();

    ListProperty<String> getBasePackages();

    Property<UnmarkedNullness> getUnmarkedNullness();

    DirectoryProperty getOutputDir();
  }

  @Override
  public void execute() {
    Parameters parameters = getParameters();
    var config =
        new GeneratorConfig(
            parameters.getBasePackages().get(),
            parameters.getOutputDir().get().getAsFile().toPath(),
            parameters.getUnmarkedNullness().get());
    List<Path> classDirs =
        parameters.getClassesDirs().getFiles().stream().map(File::toPath).toList();
    // Parent-first delegation to the worker's class loader: Spring and Jackson resolve to the
    // classes RecordBridge was built with, so the annotations on the application classes match.
    try (var loader = new URLClassLoader(urls(parameters), getClass().getClassLoader())) {
      GeneratorResult result = Generator.generate(config, classDirs, loader);
      List<String> tooNew =
          result.skippedClasses().stream()
              .filter(c -> c.contains("UnsupportedClassVersionError"))
              .toList();
      if (!tooNew.isEmpty()) {
        throw new GradleException(
            "RecordBridge: classes were compiled for a newer Java than "
                + System.getProperty("java.version")
                + "; set the generateTypeScript task's javaLauncher (it defaults to the project's"
                + " toolchain). First: "
                + tooNew.get(0));
      }
      result.skippedClasses().forEach(c -> LOGGER.warn("RecordBridge skipped {}", c));
      LOGGER.lifecycle(
          "RecordBridge: {} controllers, {} types → {}",
          result.controllers(),
          result.types(),
          config.outputDir());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GradleException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new GradleException("RecordBridge: " + e.getMessage(), e);
    }
  }

  private static URL[] urls(Parameters parameters) {
    var urls = new ArrayList<URL>();
    try {
      for (File file : parameters.getClassesDirs().plus(parameters.getRuntimeClasspath())) {
        urls.add(file.toURI().toURL());
      }
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
    return urls.toArray(URL[]::new);
  }
}
