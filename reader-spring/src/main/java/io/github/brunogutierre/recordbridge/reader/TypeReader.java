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
import java.lang.reflect.Type;
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
  private final Polymorphism polymorphism;
  private final Map<Class<?>, TypeDecl> declarations = new LinkedHashMap<>();
  private final Map<String, Class<?>> classesByName = new HashMap<>();
  private final Map<String, TypeDecl> enumNames = new LinkedHashMap<>();
  private final PagedModels pagedModels = new PagedModels();

  /** Creates a reader that treats unannotated types outside {@code @NullMarked} as non-null. */
  public TypeReader(ObjectMapper mapper) {
    this(mapper, UnmarkedNullness.NON_NULL);
  }

  /** Creates a reader that follows the rules of the given mapper. */
  public TypeReader(ObjectMapper mapper, UnmarkedNullness unmarked) {
    this.mapper = mapper;
    this.context = mapper._serializationContext();
    this.unmarked = unmarked;
    this.polymorphism = new Polymorphism(context);
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
    return read(type, declaration, where, Map.of());
  }

  /**
   * Reads a type declared in a generic supertype, replacing its type variables with what the
   * subclass binds them to (see {@link Generics#bindings}).
   */
  public TypeRef read(
      AnnotatedType type,
      AnnotatedElement declaration,
      String where,
      Map<TypeVariable<?>, Type> bindings) {
    return ref(type, new Scope(Nullness.isMarked(declaration), bindings), declaration, where);
  }

  /** The nullness default and the type variable bindings in effect while reading a type. */
  private record Scope(boolean marked, Map<TypeVariable<?>, Type> bindings) {}

  /** The declarations collected so far, in the order they were completed. */
  public List<TypeDecl> declarations() {
    var all = new ArrayList<TypeDecl>();
    declarations.values().stream().filter(d -> d != PENDING).forEach(all::add);
    all.addAll(pagedModels.declarations());
    all.addAll(enumNames.values());
    return all;
  }

  /** Whether a declaration read so far is an enum, including {@code <Enum>Param} ones. */
  public boolean isEnum(String name) {
    return enumNames.containsKey(name)
        || declarations().stream()
            .anyMatch(d -> d.name().equals(name) && d instanceof TypeDecl.EnumType);
  }

  /**
   * The enums of a parameter type as Spring binds request parameters, path variables and headers:
   * by constant name ({@code Enum.valueOf}), not by their JSON value. When the two differ, the
   * parameter uses a separate {@code <Enum>Param} declaration of the constant names.
   */
  public TypeRef withEnumConstantNames(TypeRef ref) {
    if (ref instanceof TypeRef.ArrayOf array) {
      return new TypeRef.ArrayOf(withEnumConstantNames(array.element()));
    }
    if (ref instanceof TypeRef.OrNull orNull) {
      return TypeRef.nullable(withEnumConstantNames(orNull.type()));
    }
    Class<?> type = ref instanceof TypeRef.Named named ? classesByName.get(named.name()) : null;
    if (type != null && type.isEnum() && declarations.get(type) instanceof TypeDecl.EnumType json) {
      List<String> constants =
          Arrays.stream(type.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList();
      if (constants.equals(json.constants())) {
        return ref;
      }
      String name = json.name() + "Param";
      enumNames.putIfAbsent(name, new TypeDecl.EnumType(name, constants));
      return TypeRef.Named.of(name);
    }
    return ref;
  }

  private TypeRef ref(
      AnnotatedType type, Scope scope, @Nullable AnnotatedElement declaration, String where) {
    TypeRef structure = structure(type, scope, where);
    return isNullable(type, scope.marked(), declaration) ? TypeRef.nullable(structure) : structure;
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

  private TypeRef structure(AnnotatedType type, Scope scope, String where) {
    if (type instanceof AnnotatedParameterizedType parameterized) {
      return parameterized(parameterized, scope, where);
    }
    if (type instanceof AnnotatedArrayType array) {
      if (array.getType() == byte[].class) {
        return TypeRef.STRING;
      }
      return new TypeRef.ArrayOf(
          ref(array.getAnnotatedGenericComponentType(), scope, null, where + "[]"));
    }
    if (type instanceof AnnotatedTypeVariable variable) {
      TypeVariable<?> declared = (TypeVariable<?>) variable.getType();
      Type bound = scope.bindings().get(declared);
      if (bound != null) {
        return ref(PlainTypes.of(bound), new Scope(scope.marked(), Map.of()), null, where);
      }
      return new TypeRef.Variable(declared.getName());
    }
    if (type instanceof AnnotatedWildcardType wildcard) {
      if (wildcard.getAnnotatedLowerBounds().length > 0) {
        return TypeRef.UNKNOWN;
      }
      return ref(wildcard.getAnnotatedUpperBounds()[0], scope, null, where);
    }
    return readClass((Class<?>) type.getType(), where);
  }

  private TypeRef parameterized(AnnotatedParameterizedType type, Scope scope, String where) {
    Class<?> raw = (Class<?>) ((ParameterizedType) type.getType()).getRawType();
    AnnotatedType[] arguments = type.getAnnotatedActualTypeArguments();
    if (raw == Optional.class) {
      return TypeRef.nullable(ref(arguments[0], scope, null, where)); // empty is written as null
    }
    if (PagedModels.isPage(raw)) {
      return pagedModels.of(ref(arguments[0], scope, null, where + "<0>"));
    }
    if (Iterable.class.isAssignableFrom(raw)) {
      return new TypeRef.ArrayOf(ref(arguments[0], scope, null, where + "[]"));
    }
    if (Map.class.isAssignableFrom(raw)) {
      return new TypeRef.MapOf(mapKey(arguments[0], where), ref(arguments[1], scope, null, where));
    }
    var refs = new ArrayList<TypeRef>();
    for (int i = 0; i < arguments.length; i++) {
      refs.add(ref(arguments[i], scope, null, where + "<" + i + ">"));
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
      TypeDecl declaration;
      if (type.isEnum()) {
        declaration = enumType(type, name);
      } else if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
        declaration = unionType(type, name, where);
      } else {
        declaration = objectType(type, name, description);
      }
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

  /**
   * Reads an abstract type as the union of its concrete subtypes. A generic variant keeps the type
   * parameters it passes straight to the union, such as {@code Ok<T> implements Result<T>}.
   */
  private TypeDecl unionType(Class<?> type, String name, String where) {
    List<Class<?>> subtypes = polymorphism.concreteSubtypes(type);
    if (subtypes.isEmpty()) {
      throw new UnsupportedTypeException(
          "Abstract type "
              + type.getName()
              + " at "
              + where
              + " has no known subtypes: make it sealed or list them with @JsonSubTypes");
    }
    String discriminator = polymorphism.discriminator(type);
    var variants = new ArrayList<TypeDecl.Variant>();
    for (Class<?> subtype : subtypes) {
      TypeRef ref = declare(subtype, variantArguments(type, subtype), where);
      if (!(ref instanceof TypeRef.Named named)) {
        throw new UnsupportedTypeException(
            subtype.getName() + " is a variant of " + type.getName() + " but is not an object");
      }
      String tag = polymorphism.tag(subtype).map(TypeDecl.Tag::value).orElse(null);
      variants.add(new TypeDecl.Variant(discriminator == null ? null : tag, named));
    }
    return new TypeDecl.UnionType(name, typeParameters(type), discriminator, variants);
  }

  private static List<TypeRef> variantArguments(Class<?> union, Class<?> variant) {
    var arguments = new ArrayList<TypeRef>();
    Type[] passed = argumentsPassedTo(union, variant);
    TypeVariable<?>[] unionParameters = union.getTypeParameters();
    for (TypeVariable<?> parameter : variant.getTypeParameters()) {
      TypeRef argument = TypeRef.UNKNOWN;
      for (int i = 0; i < passed.length; i++) {
        if (passed[i].equals(parameter)) {
          argument = new TypeRef.Variable(unionParameters[i].getName());
        }
      }
      arguments.add(argument);
    }
    return arguments;
  }

  /** The type arguments a class passes to a direct supertype, or none if it isn't direct. */
  private static Type[] argumentsPassedTo(Class<?> supertype, Class<?> type) {
    var direct = new ArrayList<Type>(List.of(type.getGenericInterfaces()));
    if (type.getGenericSuperclass() != null) {
      direct.add(type.getGenericSuperclass());
    }
    for (Type candidate : direct) {
      if (candidate instanceof ParameterizedType parameterized
          && parameterized.getRawType() == supertype) {
        return parameterized.getActualTypeArguments();
      }
    }
    return new Type[0];
  }

  private static List<String> typeParameters(Class<?> type) {
    return Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName).toList();
  }

  private TypeDecl objectType(Class<?> type, String name, BeanDescription description) {
    Optional<TypeDecl.Tag> tag = polymorphism.tag(type);
    boolean tagReplacesProperty = tag.isPresent() && polymorphism.tagIsExistingProperty(type);
    JsonInclude.Value classInclusion =
        description.findPropertyInclusion(context.getConfig().getDefaultPropertyInclusion(type));
    var properties = new ArrayList<Property>();
    for (BeanPropertyDefinition definition : description.findProperties()) {
      boolean isTag = tagReplacesProperty && definition.getName().equals(tag.get().property());
      if (definition.couldSerialize() && !isTag) {
        JsonInclude.Include inclusion =
            classInclusion.withOverrides(definition.findInclusion()).getValueInclusion();
        properties.add(property(type, definition, inclusion));
      }
    }
    return new TypeDecl.ObjectType(
        name, typeParameters(type), properties, tag.map(List::of).orElse(List.of()));
  }

  /**
   * Reads one property. Whether it may be missing from the JSON follows its {@code @JsonInclude}
   * rule: with {@code NON_NULL} a nullable value is left out instead of written as {@code null}.
   */
  private Property property(
      Class<?> owner, BeanPropertyDefinition definition, JsonInclude.Include inclusion) {
    Member member = definition.getAccessor().getMember();
    String path = owner.getSimpleName() + "." + definition.getName();
    TypeRef type =
        read(
            annotatedType(member),
            (AnnotatedElement) member,
            path,
            Generics.bindings(owner, member.getDeclaringClass()));
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
