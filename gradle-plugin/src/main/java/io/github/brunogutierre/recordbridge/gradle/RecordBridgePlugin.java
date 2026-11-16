package io.github.brunogutierre.recordbridge.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Adds the {@code recordBridge} extension and the {@code generateTypeScript} task to a Java
 * project. The task reads the {@code main} source set's compiled classes.
 */
public final class RecordBridgePlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    var extension = project.getExtensions().create("recordBridge", RecordBridgeExtension.class);
    extension.getBasePackages().convention(java.util.List.of());
    extension.getUnmarkedNullness().convention("NON_NULL");
    extension
        .getOutputDir()
        .convention(project.getLayout().getBuildDirectory().dir("generated/recordbridge"));

    project
        .getPlugins()
        .withType(
            JavaPlugin.class,
            java -> {
              SourceSet main =
                  project
                      .getExtensions()
                      .getByType(SourceSetContainer.class)
                      .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
              project
                  .getTasks()
                  .register(
                      "generateTypeScript",
                      GenerateTypeScriptTask.class,
                      task -> {
                        task.setGroup("build");
                        task.setDescription(
                            "Generates TypeScript types and Angular services from the controllers.");
                        task.getClassesDirs().from(main.getOutput().getClassesDirs());
                        task.getRuntimeClasspath().from(main.getRuntimeClasspath());
                        task.getBasePackages().set(extension.getBasePackages());
                        task.getUnmarkedNullness().set(extension.getUnmarkedNullness());
                        task.getOutputDir().set(extension.getOutputDir());
                        task.dependsOn(main.getClassesTaskName());
                        // Load the classes with the Java they were compiled for.
                        var toolchain =
                            project
                                .getExtensions()
                                .getByType(JavaPluginExtension.class)
                                .getToolchain();
                        task.getJavaLauncher()
                            .convention(
                                project
                                    .getExtensions()
                                    .getByType(JavaToolchainService.class)
                                    .launcherFor(toolchain));
                      });
            });
  }
}
