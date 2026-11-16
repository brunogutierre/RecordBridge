package io.github.brunogutierre.recordbridge.reader;

/** How to treat unannotated reference types in code that is not {@code @NullMarked}. */
public enum UnmarkedNullness {
  /** Unannotated types are non-null: fewer {@code | null} in TypeScript, as most DTOs expect. */
  NON_NULL,
  /** Unannotated types may be null: safer for legacy code that returns nulls freely. */
  NULLABLE
}
