package io.github.brunogutierre.recordbridge.generator;

import java.util.List;

/**
 * What a generation run did.
 *
 * @param controllers how many controllers were read
 * @param types how many type declarations were written
 * @param files the names of the files written
 * @param skippedClasses classes that could not be loaded, with the reason
 */
public record GeneratorResult(
    int controllers, int types, List<String> files, List<String> skippedClasses) {
  public GeneratorResult {
    files = List.copyOf(files);
    skippedClasses = List.copyOf(skippedClasses);
  }
}
