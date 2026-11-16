package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Turns Java types into {@link TypeRef}s and collects the {@link TypeDecl}s they need.
 *
 * <p>Jackson decides <em>which</em> properties a class has, how they are named and when they are
 * left out, so {@code @JsonProperty}, {@code @JsonIgnore}, {@code @JsonInclude} and
 * {@code @JsonValue} are honored exactly as they are at runtime. The property <em>types</em> come
 * from Java reflection, which keeps type variables and nullness annotations that Jackson's resolved
 * types erase.
 */
public final class TypeReader {

  /** Marks a class whose declaration is being read, so recursive references don't loop. */
  private static final TypeDecl PENDING = new TypeDecl.EnumType("Pending", List.of("PENDING"));

  private final ObjectMapper mapper;
  private final SerializationContext context;
  private final UnmarkedNullness unmarked;
  private final Map<Class<?>, TypeDecl> declarations = new LinkedHashMap<>();
  private final Map<String, Class<?>> classesByName = new HashMap<>();

  /** Creates a reader that treats unannotated types outside {@code @NullMarked} as non-null. */
  public TypeReader(ObjectMapper mapper) {
    this(mapper, UnmarkedNullness.NON_NULL);
  }

  /** Creates a reader that follows the rules of the given mapper. */
  public TypeReader(ObjectMapper mapper, UnmarkedNullness unmarked) {
    this.mapper = mapper;
    this.context = mapper._serializationContext();
    this.unmarked = unmarked;
  }

  /** Reads a type written as a class, with no type arguments. */
  public TypeRef read(Class<?> type) {
    return readClass(type, type.getName());
  }

  /**
   * Reads a type as it appears in a declaration.
   *
   * @param type the annotated type, such as {@link Method#getAnnotatedReturnType()}
   * @param declaration the method, parameter or field that declares it; its annotations and its
   *     {@code @NullMarked} scope decide the nullness of the top-level type
   * @param where a human-readable location used in error messages
   */
  public TypeRef read(AnnotatedType type, AnnotatedElement declaration, String where) {
    return ref(type, Nullness.isMarked(declaration), declaration, where);
  }

  /** The declarations collected so far, in the order they were completed. */
  public List<TypeDecl> declarations() {
    return declarations.values().stream().filter(d -> d != PENDING).toList();
  }

  private TypeRef ref(
      AnnotatedType type, boolean marked, @Nullable AnnotatedElement declaration, String where) {
    TypeRef structure = structure(type, marked, where);
    return isNullable(type, marked, declaration) ? TypeRef.nullable(structure) : structure;
  }

  private boolean isNullable(
      AnnotatedType type, boolean marked, @Nullable AnnotatedElement declaration) {
    if (type.getType() instanceof Class<?> c && c.isPrimitive()) {
      return false;
    }
    Boolean declared = Nullness.declared(type, declaration);
    if (declared != null) {
      return declared;
    }
    if (type instanceof AnnotatedTypeVariable || type instanceof AnnotatedWildcardType) {
      return false; // the nullness comes from the type argument or the bound
    }
    return !marked && unmarked == UnmarkedNullness.NULLABLE;
  }

  private TypeRef structure(AnnotatedType type, boolean marked, String where) {
    if (type instanceof AnnotatedParameterizedType parameterized) {
      return parameterized(parameterized, marked, where);
    }
    if (type instanceof AnnotatedArrayType array) {
      if (array.getType() == byte[].class) {
        return TypeRef.STRING;
      }
      return new TypeRef.ArrayOf(
          ref(array.getAnnotatedGenericComponentType(), marked, null, where + "[]"));
    }
    if (type instanceof AnnotatedTypeVariable variable) {
      return new TypeRef.Variable(((TypeVariable<?>) variable.getType()).getName());
    }
    if (type instanceof AnnotatedWildcardType wildcard) {
      if (wildcard.getAnnotatedLowerBounds().length > 0) {
        return TypeRef.UNKNOWN;
      }
      return ref(wildcard.getAnnotatedUpperBounds()[0], marked, null, where);
    }
    return readClass((Class<?>) type.getType(), where);
  }

  private TypeRef parameterized(AnnotatedParameterizedType type, boolean marked, String where) {
    Class<?> raw = (Class<?>) ((ParameterizedType) type.getType()).getRawType();
    AnnotatedType[] arguments = type.getAnnotatedActualTypeArguments();
    if (raw == Optional.class) {
      return TypeRef.nullable(ref(arguments[0], marked, null, where)); // empty is written as null
    }
    if (Iterable.class.isAssignableFrom(raw)) {
      return new TypeRef.ArrayOf(ref(arguments[0], marked, null, where + "[]"));
    }
    if (Map.class.isAssignableFrom(raw)) {
      return new TypeRef.MapOf(mapKey(arguments[0], where), ref(arguments[1], marked, null, where));
    }
    var refs = new ArrayList<TypeRef>();
    for (int i = 0; i < arguments.length; i++) {
      refs.add(ref(arguments[i], marked, null, where + "<" + i + ">"));
    }
    return declare(raw, refs, where);
  }

  private TypeRef mapKey(AnnotatedType key, String where) {
    if (key.getType() instanceof Class<?> keyClass && keyClass.isEnum()) {
      return declare(keyClass, List.of(), where);
    }
    return TypeRef.STRING; // Jackson writes every other key with toString()
  }

  private TypeRef readClass(Class<?> type, String where) {
    TypeRef scalar = Scalars.of(type);
    if (scalar != null) {
      return scalar;
    }
    TypeRef temporal = Temporals.of(type, mapper);
    if (temporal != null) {
      return temporal;
    }
    if (type == OptionalInt.class || type == OptionalLong.class || type == OptionalDouble.class) {
      return TypeRef.nullable(TypeRef.NUMBER);
    }
    if (type == Optional.class) {
      return TypeRef.nullable(TypeRef.UNKNOWN);
    }
    if (type.isArray()) {
      return new TypeRef.ArrayOf(readClass(type.getComponentType(), where + "[]"));
    }
    if (Iterable.class.isAssignableFrom(type)) {
      return new TypeRef.ArrayOf(TypeRef.UNKNOWN);
    }
    if (Map.class.isAssignableFrom(type)) {
      return new TypeRef.MapOf(TypeRef.STRING, TypeRef.UNKNOWN);
    }
    var arguments = new ArrayList<TypeRef>();
    for (int i = 0; i < type.getTypeParameters().length; i++) {
      arguments.add(TypeRef.UNKNOWN); // raw use of a generic type
    }
    return declare(type, arguments, where);
  }

  private TypeRef declare(Class<?> type, List<TypeRef> arguments, String where) {
    boolean jdk = type.getName().startsWith("java.") || type.getName().startsWith("javax.");
    if (type.isPrimitive() || (jdk && !type.isEnum())) {
      throw new UnsupportedTypeException("Unsupported JDK type " + type.getName() + " at " + where);
    }
    BeanDescription description = context.introspectBeanDescription(context.constructType(type));
    AnnotatedMember jsonValue = description.findJsonValueAccessor();
    if (jsonValue != null && !type.isEnum()) {
      // A value class such as `record Email(@JsonValue String value)` is written as its value.
      var member = (AnnotatedElement) jsonValue.getMember();
      return read(annotatedType(jsonValue.getMember()), member, where);
    }
    String name = nameOf(type);
    if (!declarations.containsKey(type)) {
      declarations.put(type, PENDING); // recursive types refer to themselves while being read
      TypeDecl declaration =
          type.isEnum() ? enumType(type, name) : objectType(type, name, description, where);
      declarations.remove(type);
      declarations.put(type, declaration);
    }
    return new TypeRef.Named(name, arguments);
  }

  private String nameOf(Class<?> type) {
    String name = type.getSimpleName();
    Class<?> previous = classesByName.putIfAbsent(name, type);
    if (previous != null && previous != type) {
      throw new UnsupportedTypeException(
          "Two classes map to the TypeScript name "
              + name
              + ": "
              + previous.getName()
              + " and "
              + type.getName()
              + ". Rename one of them.");
    }
    return name;
  }

  private TypeDecl enumType(Class<?> type, String name) {
    var constants = new ArrayList<String>();
    for (Object constant : type.getEnumConstants()) {
      var json = mapper.valueToTree(constant);
      if (!json.isString()) {
        throw new UnsupportedTypeException(
            "Enum " + type.getName() + " is not written as JSON strings");
      }
      constants.add(json.asString());
    }
    return new TypeDecl.EnumType(name, constants);
  }

  private TypeDecl objectType(
      Class<?> type, String name, BeanDescription description, String where) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      throw new UnsupportedTypeException(
          "Abstract type "
              + type.getName()
              + " at "
              + where
              + " needs a sealed hierarchy or @JsonTypeInfo to be represented");
    }
    JsonInclude.Value classInclusion =
        description.findPropertyInclusion(context.getConfig().getDefaultPropertyInclusion(type));
    var properties = new ArrayList<Property>();
    for (BeanPropertyDefinition definition : description.findProperties()) {
      if (definition.couldSerialize()) {
        JsonInclude.Include inclusion =
            classInclusion.withOverrides(definition.findInclusion()).getValueInclusion();
        properties.add(property(type, definition, inclusion));
      }
    }
    List<String> parameters =
        Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName).toList();
    return new TypeDecl.ObjectType(name, parameters, properties, List.of());
  }

  /**
   * Reads one property. Whether it may be missing from the JSON follows its {@code @JsonInclude}
   * rule: with {@code NON_NULL} a nullable value is left out instead of written as {@code null}.
   */
  private Property property(
      Class<?> owner, BeanPropertyDefinition definition, JsonInclude.Include inclusion) {
    Member member = definition.getAccessor().getMember();
    String path = owner.getSimpleName() + "." + definition.getName();
    TypeRef type = read(annotatedType(member), (AnnotatedElement) member, path);
    boolean isOptional = Optional.class.isAssignableFrom(definition.getRawPrimaryType());
    boolean omitted =
        switch (inclusion) {
          case NON_NULL -> type instanceof TypeRef.OrNull && !isOptional;
          case NON_ABSENT -> type instanceof TypeRef.OrNull;
          case NON_EMPTY, NON_DEFAULT, CUSTOM -> true;
          case ALWAYS, USE_DEFAULTS -> false;
        };
    if (omitted && type instanceof TypeRef.OrNull orNull) {
      type = orNull.type();
    }
    return new Property(definition.getName(), type, omitted);
  }

  private static AnnotatedType annotatedType(Member member) {
    if (member instanceof Method method) {
      return method.getAnnotatedReturnType();
    }
    return ((Field) member).getAnnotatedType();
  }
}
