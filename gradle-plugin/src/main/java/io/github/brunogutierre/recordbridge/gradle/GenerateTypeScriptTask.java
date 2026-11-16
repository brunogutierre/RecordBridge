package io.github.brunogutierre.recordbridge.gradle;

import io.github.brunogutierre.recordbridge.reader.UnmarkedNullness;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkerExecutor;

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

  /**
   * The Java that runs the generation; it must be able to load the compiled classes. Defaults to
   * the project's toolchain.
   */
  @Nested
  public abstract Property<JavaLauncher> getJavaLauncher();

  /** Runs the work in a separate process. */
  @Inject
  protected abstract WorkerExecutor getWorkerExecutor();

  @TaskAction
  void generate() {
    UnmarkedNullness nullness = unmarkedNullness();
    JavaLauncher launcher = getJavaLauncher().get();
    getWorkerExecutor()
        .processIsolation(spec -> {
          spec.getForkOptions().setExecutable(launcher.getExecutablePath().getAsFile());
          spec.getForkOptions().setMaxHeapSize("512m");
        })
        .submit(
            GenerateWork.class,
            parameters -> {
              parameters.getClassesDirs().from(getClassesDirs());
              parameters.getRuntimeClasspath().from(getRuntimeClasspath());
              parameters.getBasePackages().set(getBasePackages());
              parameters.getUnmarkedNullness().set(nullness);
              parameters.getOutputDir().set(getOutputDir());
            });
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
}
