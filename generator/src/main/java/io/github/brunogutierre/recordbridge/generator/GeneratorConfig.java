package io.github.brunogutierre.recordbridge.generator;

import io.github.brunogutierre.recordbridge.reader.UnmarkedNullness;
import java.nio.file.Path;
import java.util.List;

/**
 * What to generate and where.
 *
 * @param basePackages the packages whose classes are read, including subpackages; empty reads every
 *     compiled class
 * @param outputDir the directory the TypeScript files are written to
 * @param unmarkedNullness how unannotated types outside {@code @NullMarked} code are treated
 */
public record GeneratorConfig(
    List<String> basePackages, Path outputDir, UnmarkedNullness unmarkedNullness) {
  public GeneratorConfig {
    basePackages = List.copyOf(basePackages);
  }
}
