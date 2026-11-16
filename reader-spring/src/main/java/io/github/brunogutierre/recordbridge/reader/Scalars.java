package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** The JSON scalar each well-known Java type is written as by Jackson's defaults. */
final class Scalars {

  private static final Map<Class<?>, TypeRef> SCALARS =
      Map.ofEntries(
          Map.entry(String.class, TypeRef.STRING),
          Map.entry(char.class, TypeRef.STRING),
          Map.entry(Character.class, TypeRef.STRING),
          Map.entry(CharSequence.class, TypeRef.STRING),
          Map.entry(UUID.class, TypeRef.STRING),
          Map.entry(URI.class, TypeRef.STRING),
          Map.entry(URL.class, TypeRef.STRING),
          Map.entry(Locale.class, TypeRef.STRING),
          Map.entry(Currency.class, TypeRef.STRING),
          Map.entry(byte[].class, TypeRef.STRING), // Base64
          Map.entry(boolean.class, TypeRef.BOOLEAN),
          Map.entry(Boolean.class, TypeRef.BOOLEAN),
          Map.entry(byte.class, TypeRef.NUMBER),
          Map.entry(Byte.class, TypeRef.NUMBER),
          Map.entry(short.class, TypeRef.NUMBER),
          Map.entry(Short.class, TypeRef.NUMBER),
          Map.entry(int.class, TypeRef.NUMBER),
          Map.entry(Integer.class, TypeRef.NUMBER),
          Map.entry(long.class, TypeRef.NUMBER),
          Map.entry(Long.class, TypeRef.NUMBER),
          Map.entry(float.class, TypeRef.NUMBER),
          Map.entry(Float.class, TypeRef.NUMBER),
          Map.entry(double.class, TypeRef.NUMBER),
          Map.entry(Double.class, TypeRef.NUMBER),
          Map.entry(BigInteger.class, TypeRef.NUMBER),
          Map.entry(BigDecimal.class, TypeRef.NUMBER),
          Map.entry(Number.class, TypeRef.NUMBER),
          Map.entry(Object.class, TypeRef.UNKNOWN));

  private Scalars() {}

  /** The scalar for a class, or {@code null} when the class is not a scalar. */
  static @Nullable TypeRef of(Class<?> type) {
    if (JsonNode.class.isAssignableFrom(type)) {
      return TypeRef.UNKNOWN;
    }
    return SCALARS.get(type);
  }
}
