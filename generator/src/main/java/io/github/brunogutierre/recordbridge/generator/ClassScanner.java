package io.github.brunogutierre.recordbridge.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Lists the classes compiled into a directory. */
final class ClassScanner {

  private ClassScanner() {}

  /**
   * The binary names of the classes in a directory, in the given packages and their subpackages,
   * sorted. Anonymous and local classes are left out; {@code package-info} and {@code module-info}
   * too.
   */
  static List<String> classNames(Path directory, List<String> basePackages) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(directory)) {
      return files
          .filter(f -> f.toString().endsWith(".class"))
          .map(f -> binaryName(directory.relativize(f)))
          .filter(ClassScanner::isNamedClass)
          .filter(name -> inPackages(name, basePackages))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Can't list the classes in " + directory, e);
    }
  }

  private static String binaryName(Path relative) {
    String path = relative.toString().replace(relative.getFileSystem().getSeparator(), ".");
    return path.substring(0, path.length() - ".class".length());
  }

  private static boolean isNamedClass(String name) {
    String simple = name.substring(name.lastIndexOf('.') + 1);
    return !simple.equals("package-info")
        && !simple.equals("module-info")
        && !simple.matches(".*\\$\\d.*"); // anonymous Foo$1 and local Foo$1Bar
  }

  private static boolean inPackages(String name, List<String> basePackages) {
    return basePackages.isEmpty() || basePackages.stream().anyMatch(p -> name.startsWith(p + "."));
  }
}
