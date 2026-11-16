package io.github.brunogutierre.recordbridge.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads nullness from JSpecify and from other {@code @Nullable} annotations.
 *
 * <p>Annotations are matched by name, not by class, so they are recognized even when the
 * application classes are loaded by a different class loader than RecordBridge.
 */
final class Nullness {

  private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
  private static final String NULL_UNMARKED = "org.jspecify.annotations.NullUnmarked";

  private Nullness() {}

  /** Whether the annotations say nullable ({@code TRUE}), non-null ({@code FALSE}) or nothing. */
  static @Nullable Boolean declared(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      String name = annotation.annotationType().getSimpleName();
      if (name.equals("Nullable") || name.equals("CheckForNull")) {
        return Boolean.TRUE;
      }
      if (name.equals("NonNull") || name.equals("Nonnull") || name.equals("NotNull")) {
        return Boolean.FALSE;
      }
    }
    return null;
  }

  /** Whether a type use is annotated nullable, falling back to the declaration annotations. */
  static @Nullable Boolean declared(AnnotatedType type, @Nullable AnnotatedElement declaration) {
    Boolean onType = declared(type.getAnnotations());
    if (onType != null || declaration == null) {
      return onType;
    }
    return declared(declaration.getAnnotations());
  }

  /**
   * Whether the element is inside a {@code @NullMarked} scope: the closest of the element, its
   * method, its enclosing classes, the package and the module that carries {@code @NullMarked} or
   * {@code @NullUnmarked} decides.
   */
  static boolean isMarked(AnnotatedElement element) {
    for (AnnotatedElement scope : scopes(element)) {
      for (Annotation annotation : scope.getDeclaredAnnotations()) {
        String name = annotation.annotationType().getName();
        if (name.equals(NULL_MARKED)) {
          return true;
        }
        if (name.equals(NULL_UNMARKED)) {
          return false;
        }
      }
    }
    return false;
  }

  private static List<AnnotatedElement> scopes(AnnotatedElement element) {
    var scopes = new ArrayList<AnnotatedElement>();
    AnnotatedElement current = element;
    if (current instanceof Parameter parameter) {
      scopes.add(parameter);
      current = parameter.getDeclaringExecutable();
    }
    Class<?> type;
    if (current instanceof Class<?> declared) {
      type = declared;
    } else {
      scopes.add(current);
      type = ((Member) current).getDeclaringClass();
    }
    Class<?> outermost = type;
    for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
      scopes.add(c);
      outermost = c;
    }
    if (outermost.getPackage() != null) {
      scopes.add(outermost.getPackage());
    }
    scopes.add(outermost.getModule());
    return scopes;
  }
}
