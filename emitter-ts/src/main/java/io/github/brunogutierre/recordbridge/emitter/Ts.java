package io.github.brunogutierre.recordbridge.emitter;

import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Renders model fragments as TypeScript source. */
final class Ts {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  private Ts() {}

  /** A type reference, such as {@code Page<Order> | null}. */
  static String type(TypeRef ref) {
    if (ref instanceof TypeRef.Primitive primitive) {
      return switch (primitive.kind()) {
        case STRING -> "string";
        case NUMBER -> "number";
        case BOOLEAN -> "boolean";
        case UNKNOWN -> "unknown";
      };
    }
    if (ref instanceof TypeRef.ArrayOf array) {
      String element = type(array.element());
      return (array.element() instanceof TypeRef.OrNull ? "(" + element + ")" : element) + "[]";
    }
    if (ref instanceof TypeRef.MapOf map) {
      String record = "Record<" + type(map.key()) + ", " + type(map.value()) + ">";
      // Not every enum constant has to be present as a key.
      return map.key() instanceof TypeRef.Named ? "Partial<" + record + ">" : record;
    }
    if (ref instanceof TypeRef.Named named) {
      return named.name() + typeArguments(named.arguments());
    }
    if (ref instanceof TypeRef.Variable variable) {
      return variable.name();
    }
    return type(((TypeRef.OrNull) ref).type()) + " | null";
  }

  /** Type arguments such as {@code <string, Order>}, or nothing. */
  static String typeArguments(List<TypeRef> arguments) {
    if (arguments.isEmpty()) {
      return "";
    }
    return arguments.stream().map(Ts::type).collect(Collectors.joining(", ", "<", ">"));
  }

  /** Type parameters such as {@code <T>}, or nothing. */
  static String typeParameters(List<String> parameters) {
    return parameters.isEmpty() ? "" : "<" + String.join(", ", parameters) + ">";
  }

  /** A property key: bare when it is an identifier, quoted otherwise. */
  static String key(String name) {
    return IDENTIFIER.matcher(name).matches() ? name : string(name);
  }

  /** A single-quoted string literal. */
  static String string(String value) {
    var out = new StringBuilder("'");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '\'' -> out.append("\\'");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20 || c == 0x2028 || c == 0x2029) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('\'').toString();
  }
}
