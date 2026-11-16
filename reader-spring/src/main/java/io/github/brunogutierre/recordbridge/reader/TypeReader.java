package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Turns Java types into {@link TypeRef}s and collects the {@link TypeDecl}s they need.
 *
 * <p>Jackson decides <em>which</em> properties a class has and how they are named, so
 * {@code @JsonProperty}, {@code @JsonIgnore} and {@code @JsonValue} are honored exactly as they are
 * at runtime. The property <em>types</em> come from Java reflection, which keeps type variables and
 * type annotations that Jackson's resolved types erase.
 */
public final class TypeReader {

  private final ObjectMapper mapper;
  private final SerializationContext context;
  private final Map<Class<?>, TypeDecl> declarations = new LinkedHashMap<>();
  private final Map<String, Class<?>> classesByName = new HashMap<>();

  /** Creates a reader that follows the rules of the given mapper. */
  public TypeReader(ObjectMapper mapper) {
    this.mapper = mapper;
    this.context = mapper._serializationContext();
  }

  /** Reads a type written as a class, with no type arguments. */
  public TypeRef read(Class<?> type) {
    return readClass(type, type.getName());
  }

  /** Reads a type as it appears in a declaration: a return type, parameter or field. */
  public TypeRef read(AnnotatedType type, String where) {
    if (type instanceof AnnotatedParameterizedType parameterized) {
      return readParameterized(parameterized, where);
    }
    if (type instanceof AnnotatedArrayType array) {
      if (array.getType() == byte[].class) {
        return TypeRef.STRING;
      }
      return new TypeRef.ArrayOf(read(array.getAnnotatedGenericComponentType(), where + "[]"));
    }
    if (type instanceof AnnotatedTypeVariable variable) {
      return new TypeRef.Variable(((TypeVariable<?>) variable.getType()).getTypeName());
    }
    if (type instanceof AnnotatedWildcardType wildcard) {
      AnnotatedType[] lower = wildcard.getAnnotatedLowerBounds();
      if (lower.length > 0) {
        return TypeRef.UNKNOWN;
      }
      return read(wildcard.getAnnotatedUpperBounds()[0], where);
    }
    return readClass((Class<?>) type.getType(), where);
  }

  /** The declarations collected so far, in the order they were completed. */
  public List<TypeDecl> declarations() {
    return declarations.values().stream().filter(d -> d != PENDING).toList();
  }

  private TypeRef readParameterized(AnnotatedParameterizedType type, String where) {
    Class<?> raw = (Class<?>) ((java.lang.reflect.ParameterizedType) type.getType()).getRawType();
    AnnotatedType[] arguments = type.getAnnotatedActualTypeArguments();
    if (Iterable.class.isAssignableFrom(raw)) {
      return new TypeRef.ArrayOf(read(arguments[0], where + "[]"));
    }
    if (Map.class.isAssignableFrom(raw)) {
      return new TypeRef.MapOf(mapKey(arguments[0], where), read(arguments[1], where + "{}"));
    }
    var refs = new ArrayList<TypeRef>();
    for (int i = 0; i < arguments.length; i++) {
      refs.add(read(arguments[i], where + "<" + i + ">"));
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
    if (type.isArray()) {
      return new TypeRef.ArrayOf(readClass(type.getComponentType(), where + "[]"));
    }
    if (Iterable.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
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
    if (type.isPrimitive()
        || type.getName().startsWith("java.")
        || type.getName().startsWith("javax.")) {
      throw new UnsupportedTypeException("Unsupported JDK type " + type.getName() + " at " + where);
    }
    BeanDescription description = context.introspectBeanDescription(context.constructType(type));
    AnnotatedMember jsonValue = description.findJsonValueAccessor();
    if (jsonValue != null && !type.isEnum()) {
      // A value class such as `record Email(@JsonValue String value)` is written as its value.
      return read(annotatedType(jsonValue.getMember()), where);
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
    var properties = new ArrayList<Property>();
    for (BeanPropertyDefinition property : description.findProperties()) {
      if (!property.couldSerialize()) {
        continue;
      }
      AnnotatedType propertyType = annotatedType(property.getAccessor().getMember());
      String path = type.getSimpleName() + "." + property.getName();
      properties.add(Property.required(property.getName(), read(propertyType, path)));
    }
    List<String> parameters =
        Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName).toList();
    return new TypeDecl.ObjectType(name, parameters, properties, List.of());
  }

  private static AnnotatedType annotatedType(Member member) {
    if (member instanceof Method method) {
      return method.getAnnotatedReturnType();
    }
    return ((Field) member).getAnnotatedType();
  }

  /** Marks a class whose declaration is being read, so recursive references don't loop. */
  private static final TypeDecl PENDING = new TypeDecl.EnumType("Pending", List.of("PENDING"));
}
