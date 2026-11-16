package io.github.brunogutierre.recordbridge.model;

/**
 * A property of an object type.
 *
 * @param name the JSON property name, which may contain characters that are not valid in an
 *     identifier
 * @param type the property type; nullable values use {@link TypeRef.OrNull}
 * @param optional whether the property may be absent from the JSON object
 */
public record Property(String name, TypeRef type, boolean optional) {
  public Property {
    Names.requireNotBlank(name, "property name");
  }

  /** A property that is always present. */
  public static Property required(String name, TypeRef type) {
    return new Property(name, type, false);
  }
}
