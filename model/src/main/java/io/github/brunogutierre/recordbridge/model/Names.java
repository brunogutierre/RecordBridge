package io.github.brunogutierre.recordbridge.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Validation helpers shared by the model records. */
final class Names {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  private Names() {}

  static String requireIdentifier(String value, String what) {
    if (!IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException(what + " is not a valid identifier: '" + value + "'");
    }
    return value;
  }

  static String requireNotBlank(String value, String what) {
    if (value.isBlank()) {
      throw new IllegalArgumentException(what + " must not be blank");
    }
    return value;
  }

  static <T> void requireUnique(Collection<T> items, Function<T, String> key, String what) {
    var seen = new HashSet<String>();
    for (T item : items) {
      String k = key.apply(item);
      if (!seen.add(k)) {
        throw new IllegalArgumentException("duplicate " + what + ": '" + k + "'");
      }
    }
  }
}
