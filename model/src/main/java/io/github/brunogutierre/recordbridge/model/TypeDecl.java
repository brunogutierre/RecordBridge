package io.github.brunogutierre.recordbridge.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A named type declared by the API: an object, an enum or a union. */
public sealed interface TypeDecl
    permits TypeDecl.ObjectType, TypeDecl.EnumType, TypeDecl.UnionType {

  /** The name of the declaration, unique in the model. */
  String name();

  /** The type parameters of the declaration, empty unless it is generic. */
  List<String> typeParameters();

  /**
   * A JSON object with fixed properties, read from a record or a bean.
   *
   * @param tags constant properties that identify the object inside a union, such as {@code type:
   *     "circle"}
   */
  record ObjectType(
      String name, List<String> typeParameters, List<Property> properties, List<Tag> tags)
      implements TypeDecl {
    public ObjectType {
      Names.requireIdentifier(name, "type name");
      typeParameters = List.copyOf(typeParameters);
      typeParameters.forEach(p -> Names.requireIdentifier(p, "type parameter"));
      Names.requireUnique(typeParameters, p -> p, "type parameter of " + name);
      properties = List.copyOf(properties);
      tags = List.copyOf(tags);
      var keys = new java.util.ArrayList<String>();
      properties.forEach(p -> keys.add(p.name()));
      tags.forEach(t -> keys.add(t.property()));
      Names.requireUnique(keys, k -> k, "property of " + name);
    }

    /** A non-generic object type without tags. */
    public static ObjectType of(String name, List<Property> properties) {
      return new ObjectType(name, List.of(), properties, List.of());
    }
  }

  /** A constant string property that identifies a union variant. */
  record Tag(String property, String value) {
    public Tag {
      Names.requireNotBlank(property, "tag property");
    }
  }

  /** A string enum; the constants are the JSON values. */
  record EnumType(String name, List<String> constants) implements TypeDecl {
    public EnumType {
      Names.requireIdentifier(name, "type name");
      constants = List.copyOf(constants);
      if (constants.isEmpty()) {
        throw new IllegalArgumentException("enum " + name + " has no constants");
      }
      Names.requireUnique(constants, c -> c, "constant of " + name);
    }

    @Override
    public List<String> typeParameters() {
      return List.of();
    }
  }

  /**
   * A closed set of object types, read from a sealed interface or a Jackson polymorphic type.
   *
   * @param discriminator the property whose value tells the variants apart, or {@code null} when
   *     Jackson deduces the variant from the properties
   */
  record UnionType(
      String name,
      List<String> typeParameters,
      @Nullable String discriminator,
      List<Variant> variants)
      implements TypeDecl {
    public UnionType {
      Names.requireIdentifier(name, "type name");
      typeParameters = List.copyOf(typeParameters);
      typeParameters.forEach(p -> Names.requireIdentifier(p, "type parameter"));
      variants = List.copyOf(variants);
      if (variants.isEmpty()) {
        throw new IllegalArgumentException("union " + name + " has no variants");
      }
      if (discriminator != null) {
        Names.requireNotBlank(discriminator, "discriminator");
        Names.requireUnique(variants, v -> String.valueOf(v.tag()), "tag of " + name);
        for (var v : variants) {
          if (v.tag() == null) {
            throw new IllegalArgumentException(
                "variant " + v.type().name() + " of " + name + " needs a tag");
          }
        }
      }
      Names.requireUnique(variants, v -> v.type().name(), "variant of " + name);
    }
  }

  /**
   * A member of a union.
   *
   * @param tag the discriminator value, or {@code null} when the union has no discriminator
   */
  record Variant(@Nullable String tag, TypeRef.Named type) {}
}
