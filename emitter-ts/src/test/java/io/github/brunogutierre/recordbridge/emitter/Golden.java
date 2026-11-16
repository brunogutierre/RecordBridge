package io.github.brunogutierre.recordbridge.emitter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compares generated code with a committed golden file, or rewrites it with -PupdateGolden. */
final class Golden {

  private Golden() {}

  static void assertMatches(String fileName, String actual) throws IOException {
    Path file = Path.of(System.getProperty("recordbridge.golden.dir")).resolve(fileName);
    if (Boolean.getBoolean("recordbridge.golden.update")) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, actual);
      return;
    }
    assertThat(file).as("golden file %s (run with -PupdateGolden to create it)", file).exists();
    assertThat(actual).isEqualTo(Files.readString(file));
  }
}
