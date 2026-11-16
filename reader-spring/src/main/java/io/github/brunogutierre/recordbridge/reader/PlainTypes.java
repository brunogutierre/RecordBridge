package io.github.brunogutierre.recordbridge.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Views a plain {@link Type} as an {@link AnnotatedType} without annotations, so types resolved
 * from generic supertypes go through the same reading code as declared ones.
 */
final class PlainTypes {

  private PlainTypes() {}

  static AnnotatedType of(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      return new Parameterized(parameterized);
    }
    if (type instanceof GenericArrayType array) {
      return new Array(array);
    }
    if (type instanceof TypeVariable<?> variable) {
      return new Variable(variable);
    }
    if (type instanceof WildcardType wildcard) {
      return new Wildcard(wildcard);
    }
    return new Plain(type);
  }

  private static AnnotatedType[] of(Type[] types) {
    return Arrays.stream(types).map(PlainTypes::of).toArray(AnnotatedType[]::new);
  }

  /** Common behavior: no annotations at all. */
  private interface NoAnnotations extends AnnotatedType {
    @Override
    default <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationClass) {
      return null;
    }

    @Override
    default Annotation[] getAnnotations() {
      return new Annotation[0];
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
      return new Annotation[0];
    }
  }

  private record Plain(Type getType) implements NoAnnotations {}

  private record Parameterized(ParameterizedType getType)
      implements NoAnnotations, AnnotatedParameterizedType {
    @Override
    public @Nullable AnnotatedType getAnnotatedOwnerType() {
      return null;
    }

    @Override
    public AnnotatedType[] getAnnotatedActualTypeArguments() {
      return of(getType.getActualTypeArguments());
    }
  }

  private record Array(GenericArrayType getType) implements NoAnnotations, AnnotatedArrayType {
    @Override
    public @Nullable AnnotatedType getAnnotatedOwnerType() {
      return null;
    }

    @Override
    public AnnotatedType getAnnotatedGenericComponentType() {
      return of(getType.getGenericComponentType());
    }
  }

  private record Variable(TypeVariable<?> getType) implements NoAnnotations, AnnotatedTypeVariable {
    @Override
    public @Nullable AnnotatedType getAnnotatedOwnerType() {
      return null;
    }

    @Override
    public AnnotatedType[] getAnnotatedBounds() {
      return of(getType.getBounds());
    }
  }

  private record Wildcard(WildcardType getType) implements NoAnnotations, AnnotatedWildcardType {
    @Override
    public @Nullable AnnotatedType getAnnotatedOwnerType() {
      return null;
    }

    @Override
    public AnnotatedType[] getAnnotatedLowerBounds() {
      return of(getType.getLowerBounds());
    }

    @Override
    public AnnotatedType[] getAnnotatedUpperBounds() {
      return of(getType.getUpperBounds());
    }
  }
}
