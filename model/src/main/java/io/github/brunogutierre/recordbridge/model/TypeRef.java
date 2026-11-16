package io.github.brunogutierre.recordbridge.model;

import java.util.List;

/** A reference to a type, as used by properties, parameters and responses. */
public sealed interface TypeRef
    permits TypeRef.Primitive,
        TypeRef.ArrayOf,
        TypeRef.MapOf,
        TypeRef.Named,
        TypeRef.Variable,
        TypeRef.OrNull {

  /** A JSON string. */
  Primitive STRING = new Primitive(PrimitiveKind.STRING);

  /** A JSON number. */
  Primitive NUMBER = new Primitive(PrimitiveKind.NUMBER);

  /** A JSON boolean. */
  Primitive BOOLEAN = new Primitive(PrimitiveKind.BOOLEAN);

  /** Any JSON value whose shape is not known. */
  Primitive UNKNOWN = new Primitive(PrimitiveKind.UNKNOWN);

  /** The kinds of JSON scalar values. */
  enum PrimitiveKind {
    STRING,
    NUMBER,
    BOOLEAN,
    UNKNOWN
  }

  /** A JSON scalar. */
  record Primitive(PrimitiveKind kind) implements TypeRef {}

  /** A JSON array whose items have the given type. */
  record ArrayOf(TypeRef element) implements TypeRef {}

  /**
   * A JSON object used as a dictionary. The key is {@link #STRING} or a {@link Named} enum, since
   * JSON keys are always strings.
   */
  record MapOf(TypeRef key, TypeRef value) implements TypeRef {}

  /** A reference to a {@link TypeDecl} of the model, with its type arguments. */
  record Named(String name, List<TypeRef> arguments) implements TypeRef {
    public Named {
      Names.requireIdentifier(name, "type name");
      arguments = List.copyOf(arguments);
    }

    /** A reference to a type declaration that has no type parameters. */
    public static Named of(String name) {
      return new Named(name, List.of());
    }
  }

  /** A type variable declared by the enclosing generic type, such as {@code T}. */
  record Variable(String name) implements TypeRef {
    public Variable {
      Names.requireIdentifier(name, "type variable");
    }
  }

  /** The wrapped type or {@code null}. */
  record OrNull(TypeRef type) implements TypeRef {
    public OrNull {
      if (type instanceof OrNull) {
        throw new IllegalArgumentException("OrNull must not wrap another OrNull");
      }
    }
  }

  /** Returns the type or null; wrapping an already nullable type returns it unchanged. */
  static TypeRef nullable(TypeRef type) {
    return type instanceof OrNull ? type : new OrNull(type);
  }
}
