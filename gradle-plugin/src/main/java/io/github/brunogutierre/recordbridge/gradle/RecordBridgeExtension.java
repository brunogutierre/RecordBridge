package io.github.brunogutierre.recordbridge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configures the generation.
 *
 * <pre>{@code
 * recordBridge {
 *     basePackages = listOf("com.example.api")
 *     outputDir = file("../web/src/app/api")
 *     unmarkedNullness = "NON_NULL" // or "NULLABLE"
 * }
 * }</pre>
 */
public abstract class RecordBridgeExtension {

  /** The packages whose classes are read, with their subpackages; empty reads every class. */
  public abstract ListProperty<String> getBasePackages();

  /** Where the TypeScript files go. Defaults to {@code build/generated/recordbridge}. */
  public abstract DirectoryProperty getOutputDir();

  /** {@code NON_NULL} (default) or {@code NULLABLE}, for types outside {@code @NullMarked}. */
  public abstract Property<String> getUnmarkedNullness();
}
