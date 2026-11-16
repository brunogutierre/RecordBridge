package io.github.brunogutierre.recordbridge.reader;

/** Thrown when a Java type cannot be represented in TypeScript. */
public final class UnsupportedTypeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  UnsupportedTypeException(String message) {
    super(message);
  }
}
