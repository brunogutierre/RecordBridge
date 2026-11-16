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
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Reads the compiled controllers and writes the Angular API layer. */
@CacheableTask
public abstract class GenerateTypeScriptTask extends DefaultTask {

  /** The directories with the compiled application classes. */
  @Classpath
  public abstract ConfigurableFileCollection getClassesDirs();

  /** The runtime classpath of the application, so its classes can be loaded. */
  @Classpath
  public abstract ConfigurableFileCollection getRuntimeClasspath();

  /** See {@link RecordBridgeExtension#getBasePackages()}. */
  @Input
  public abstract ListProperty<String> getBasePackages();

  /** See {@link RecordBridgeExtension#getUnmarkedNullness()}. */
  @Input
  public abstract Property<String> getUnmarkedNullness();

  /** See {@link RecordBridgeExtension#getOutputDir()}. */
  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @TaskAction
  void generate() {
    var config =
        new GeneratorConfig(
            getBasePackages().get(), getOutputDir().get().getAsFile().toPath(), unmarkedNullness());
    List<Path> classDirs = getClassesDirs().getFiles().stream().map(File::toPath).toList();
    // Parent-first delegation to the plugin's class loader: Spring and Jackson resolve to the
    // classes RecordBridge was built with, so the annotations on the application classes match.
    try (var loader = new URLClassLoader(urls(), getClass().getClassLoader())) {
      GeneratorResult result = Generator.generate(config, classDirs, loader);
      result.skippedClasses().forEach(c -> getLogger().warn("RecordBridge skipped {}", c));
      getLogger()
          .lifecycle(
              "RecordBridge: {} controllers, {} types → {}",
              result.controllers(),
              result.types(),
              config.outputDir());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      throw new GradleException("RecordBridge: " + e.getMessage(), e);
    }
  }

  private UnmarkedNullness unmarkedNullness() {
    String value = getUnmarkedNullness().get().toUpperCase(Locale.ROOT);
    try {
      return UnmarkedNullness.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new GradleException(
          "recordBridge.unmarkedNullness must be NON_NULL or NULLABLE, not " + value, e);
    }
  }

  private URL[] urls() throws MalformedURLException {
    var urls = new ArrayList<URL>();
    for (File file : getClassesDirs().plus(getRuntimeClasspath()).getFiles()) {
      urls.add(file.toURI().toURL());
    }
    return urls.toArray(URL[]::new);
  }
}
