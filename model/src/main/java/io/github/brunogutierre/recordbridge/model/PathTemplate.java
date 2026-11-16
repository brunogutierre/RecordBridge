package io.github.brunogutierre.recordbridge.model;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Splits a Spring path template into literal text and variables. Variables may carry a regex with
 * its own braces, such as {@code {id:\d{3}}}, or capture the rest of the path, such as {@code
 * {*path}}.
 */
public final class PathTemplate {

  private PathTemplate() {}

  /**
   * One piece of a path template.
   *
   * @param literal the text before the variable (possibly empty)
   * @param variable the variable name, or {@code null} for the trailing text
   * @param catchAll whether the variable captures the rest of the path ({@code {*path}})
   */
  public record Part(String literal, @Nullable String variable, boolean catchAll) {}

  /** The parts of a path; the last one has no variable. */
  public static List<Part> parse(String path) {
    var parts = new ArrayList<Part>();
    var literal = new StringBuilder();
    int i = 0;
    while (i < path.length()) {
      char c = path.charAt(i);
      if (c != '{') {
        literal.append(c);
        i++;
        continue;
      }
      int depth = 0;
      int end = i;
      for (; end < path.length(); end++) {
        if (path.charAt(end) == '{') {
          depth++;
        } else if (path.charAt(end) == '}' && --depth == 0) {
          break;
        }
      }
      if (end == path.length()) {
        throw new IllegalArgumentException("unbalanced braces in path " + path);
      }
      String inside = path.substring(i + 1, end);
      boolean catchAll = inside.startsWith("*");
      String name = (catchAll ? inside.substring(1) : inside).split(":", 2)[0];
      parts.add(new Part(literal.toString(), name, catchAll));
      literal.setLength(0);
      i = end + 1;
    }
    parts.add(new Part(literal.toString(), null, false));
    return parts;
  }
}
