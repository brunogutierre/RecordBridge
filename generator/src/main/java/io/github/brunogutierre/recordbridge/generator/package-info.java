/**
 * Runs RecordBridge end to end: finds the compiled classes, reads them and writes the TypeScript
 * files. The Gradle and Maven plugins are thin wrappers around {@link
 * io.github.brunogutierre.recordbridge.generator.Generator}.
 *
 * <p>Every type in this package is non-null unless annotated with {@code @Nullable}.
 */
@NullMarked
package io.github.brunogutierre.recordbridge.generator;

import org.jspecify.annotations.NullMarked;
