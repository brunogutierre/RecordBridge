package io.github.brunogutierre.recordbridge.generator;

import io.github.brunogutierre.recordbridge.emitter.AngularEmitter;
import io.github.brunogutierre.recordbridge.emitter.ModelsEmitter;
import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.reader.ApiReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/** Reads compiled classes and writes the Angular API layer. */
public final class Generator {

  private Generator() {}

  /**
   * Generates the TypeScript files.
   *
   * @param config what to generate and where
   * @param classDirectories the directories that hold the compiled application classes
   * @param loader a class loader that can load those classes and their dependencies; Spring and
   *     Jackson must resolve to the same classes RecordBridge uses, so their annotations match
   * @return what was read and written
   */
  public static GeneratorResult generate(
      GeneratorConfig config, List<Path> classDirectories, ClassLoader loader) {
    var classNames = new ArrayList<String>();
    for (Path directory : classDirectories) {
      classNames.addAll(ClassScanner.classNames(directory, config.basePackages()));
    }
    var classes = new ArrayList<Class<?>>();
    var skipped = new ArrayList<String>();
    for (String name : classNames) {
      try {
        classes.add(Class.forName(name, false, loader));
      } catch (ClassNotFoundException | LinkageError e) {
        skipped.add(name + " (" + e + ")");
      }
    }
    ApiModel model =
        new ApiReader(JsonMapper.builder().build(), config.unmarkedNullness()).read(classes);
    if (model.controllers().isEmpty()
        && classes.stream().anyMatch(Generator::looksLikeController)) {
      // The annotations are there by name but not as RecordBridge's classes: two copies of Spring.
      throw new IllegalStateException(
          "Found @RestController classes but read none of them: Spring's annotations were loaded"
              + " twice. Load the application classes in a class loader whose parent provides"
              + " Spring and Jackson.");
    }
    Map<String, String> files = new AngularEmitter().emit(model);
    write(config.outputDir(), files);
    return new GeneratorResult(
        model.controllers().size(), model.types().size(), List.copyOf(files.keySet()), skipped);
  }

  private static boolean looksLikeController(Class<?> type) {
    return java.util.Arrays.stream(type.getAnnotations())
        .anyMatch(a ->
            a.annotationType()
                .getName()
                .equals("org.springframework.web.bind.annotation.RestController"));
  }

  /**
   * Writes the files after removing the ones a previous run generated. Only files that start with
   * the RecordBridge header are removed, so pointing the output at a folder that also holds
   * hand-written code is safe.
   */
  static void write(Path directory, Map<String, String> files) {
    try {
      Files.createDirectories(directory);
      try (Stream<Path> existing = Files.list(directory)) {
        for (Path file : existing.filter(f -> f.toString().endsWith(".ts")).toList()) {
          if (isGenerated(file)) {
            Files.delete(file);
          }
        }
      }
      for (var file : files.entrySet()) {
        Files.writeString(directory.resolve(file.getKey()), file.getValue());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Can't write the TypeScript files to " + directory, e);
    }
  }

  private static boolean isGenerated(Path file) throws IOException {
    try (var lines = Files.lines(file)) {
      return lines.findFirst().map(l -> (l + "\n").equals(ModelsEmitter.HEADER)).orElse(false);
    }
  }
}
