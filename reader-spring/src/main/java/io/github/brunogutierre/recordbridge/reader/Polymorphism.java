package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.TypeSerializer;

/**
 * Asks Jackson how a polymorphic type is written: which property carries the type id and which id
 * each subtype gets. {@code @JsonTypeInfo}, {@code @JsonSubTypes}, {@code @JsonTypeName} and
 * mix-ins are therefore honored exactly as at runtime.
 */
final class Polymorphism {

  private final SerializationContext context;

  Polymorphism(SerializationContext context) {
    this.context = context;
  }

  /** The property that tells the variants of a type apart, or {@code null} if there is none. */
  @Nullable String discriminator(Class<?> type) {
    TypeSerializer serializer = taggedSerializer(type);
    return serializer == null ? null : propertyName(type, serializer);
  }

  /** The constant property Jackson writes for a concrete class, if its hierarchy is tagged. */
  Optional<TypeDecl.Tag> tag(Class<?> type) {
    TypeSerializer serializer = taggedSerializer(type);
    if (serializer == null) {
      return Optional.empty();
    }
    String id = serializer.getTypeIdResolver().idFromValueAndType(context, null, type);
    return Optional.of(new TypeDecl.Tag(propertyName(type, serializer), id));
  }

  /**
   * The type id property. Jackson's {@code EXISTING_PROPERTY} serializer doesn't report it, so it
   * falls back to the {@code @JsonTypeInfo} that applies to the class (including inherited ones).
   */
  private String propertyName(Class<?> type, TypeSerializer serializer) {
    String name = serializer.getPropertyName();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    JsonTypeInfo.Value info =
        context
            .getAnnotationIntrospector()
            .findPolymorphicTypeInfo(context.getConfig(), context.introspectClassAnnotations(type));
    if (info != null && info.getPropertyName() != null && !info.getPropertyName().isEmpty()) {
      return info.getPropertyName();
    }
    return serializer.getTypeIdResolver().getMechanism().getDefaultPropertyName();
  }

  /** Whether the tag replaces a property the class already has ({@code EXISTING_PROPERTY}). */
  boolean tagIsExistingProperty(Class<?> type) {
    TypeSerializer serializer = taggedSerializer(type);
    return serializer != null && serializer.getTypeInclusion() == JsonTypeInfo.As.EXISTING_PROPERTY;
  }

  /**
   * The concrete subtypes of an abstract type: the permitted subclasses of a sealed hierarchy (in
   * declaration order) followed by any other subtype registered with Jackson (by name).
   */
  List<Class<?>> concreteSubtypes(Class<?> type) {
    var found = new LinkedHashSet<Class<?>>();
    collectPermitted(type, found);
    var registered = new ArrayList<Class<?>>();
    var annotated = context.introspectClassAnnotations(type);
    for (NamedType named :
        context
            .getConfig()
            .getSubtypeResolver()
            .collectAndResolveSubtypesByClass(context.getConfig(), annotated)) {
      registered.add(named.getType());
    }
    registered.sort(Comparator.comparing(Class::getName));
    found.addAll(registered);
    return found.stream().filter(Polymorphism::isConcrete).toList();
  }

  private static void collectPermitted(Class<?> type, Set<Class<?>> found) {
    Class<?>[] permitted = type.getPermittedSubclasses();
    if (permitted == null) {
      return;
    }
    for (Class<?> subtype : permitted) {
      found.add(subtype);
      collectPermitted(subtype, found);
    }
  }

  private static boolean isConcrete(Class<?> type) {
    return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
  }

  private @Nullable TypeSerializer taggedSerializer(Class<?> type) {
    TypeSerializer serializer = context.findTypeSerializer(context.constructType(type));
    if (serializer == null || serializer.getTypeIdResolver() == null) {
      return null; // no type info, or DEDUCTION (no id is written)
    }
    JsonTypeInfo.Id mechanism = serializer.getTypeIdResolver().getMechanism();
    if (mechanism == JsonTypeInfo.Id.NONE || mechanism == JsonTypeInfo.Id.DEDUCTION) {
      return null;
    }
    JsonTypeInfo.As inclusion = serializer.getTypeInclusion();
    if (inclusion != JsonTypeInfo.As.PROPERTY && inclusion != JsonTypeInfo.As.EXISTING_PROPERTY) {
      throw new UnsupportedTypeException(
          type.getName()
              + " uses @JsonTypeInfo(include = "
              + inclusion
              + "); only PROPERTY and EXISTING_PROPERTY are supported");
    }
    return serializer;
  }
}
