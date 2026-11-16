/**
 * Reads compiled Spring Boot classes and builds the intermediate model, using Jackson 3
 * introspection to match the JSON shape that goes over the wire.
 *
 * <p>Every type in this package is non-null unless annotated with {@code @Nullable}.
 */
@NullMarked
package io.github.brunogutierre.recordbridge.reader;

import org.jspecify.annotations.NullMarked;
