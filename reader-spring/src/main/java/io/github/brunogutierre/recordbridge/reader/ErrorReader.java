package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Reads the bodies error responses carry: the return types of {@code @ExceptionHandler} methods in
 * {@code @ControllerAdvice} classes and in controllers, plus RFC 9457 {@code ProblemDetail}, the
 * standard error body of Spring.
 */
final class ErrorReader {

  /** The name of the built-in problem detail declaration. */
  static final String PROBLEM_DETAIL = "ProblemDetail";

  /**
   * RFC 9457 problem details as Spring writes them. Spring flattens extension properties with a
   * Jackson mix-in and leaves empty members out, so every member is optional.
   */
  static final TypeDecl PROBLEM_DETAIL_TYPE =
      TypeDecl.ObjectType.of(
          PROBLEM_DETAIL,
          List.of(
              new Property("type", TypeRef.STRING, true),
              new Property("title", TypeRef.STRING, true),
              new Property("status", TypeRef.NUMBER, true),
              new Property("detail", TypeRef.STRING, true),
              new Property("instance", TypeRef.STRING, true)));

  private final TypeReader types;

  ErrorReader(TypeReader types) {
    this.types = types;
  }

  /** The error body types, {@code ProblemDetail} first, then handler bodies in a stable order. */
  List<TypeRef> read(Collection<Class<?>> classes, Set<Class<?>> controllers) {
    var errors = new LinkedHashSet<TypeRef>();
    errors.add(TypeRef.Named.of(PROBLEM_DETAIL));
    classes.stream()
        .filter(type -> controllers.contains(type) || isAdvice(type))
        .sorted(Comparator.comparing(Class::getName))
        .flatMap(type -> handlers(type).stream())
        .forEach(handler -> errors.addAll(body(handler)));
    return new ArrayList<>(errors);
  }

  private static boolean isAdvice(Class<?> type) {
    return MergedAnnotations.from(type, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
        .isPresent("org.springframework.web.bind.annotation.ControllerAdvice");
  }

  private static List<Method> handlers(Class<?> type) {
    return Arrays.stream(type.getMethods())
        .filter(m -> AnnotatedElementUtils.hasAnnotation(m, ExceptionHandler.class))
        .sorted(Comparator.comparing(Method::getName))
        .toList();
  }

  private List<TypeRef> body(Method handler) {
    AnnotatedType body = EndpointReader.unwrap(handler.getAnnotatedReturnType());
    Class<?> raw = rawClass(body.getType());
    if (raw == void.class || raw == Void.class || raw == String.class || raw == Object.class) {
      return List.of(); // no body, plain text, or nothing typed to promise
    }
    if (ProblemDetail.class.isAssignableFrom(raw) || ErrorResponse.class.isAssignableFrom(raw)) {
      return List.of(TypeRef.Named.of(PROBLEM_DETAIL));
    }
    String where = handler.getDeclaringClass().getSimpleName() + "." + handler.getName();
    TypeRef ref = types.read(body, handler, where + " error");
    return List.of(ref instanceof TypeRef.OrNull orNull ? orNull.type() : ref);
  }

  private static Class<?> rawClass(java.lang.reflect.Type type) {
    if (type instanceof Class<?> c) {
      return c;
    }
    if (type instanceof ParameterizedType p) {
      return (Class<?>) p.getRawType();
    }
    return Object.class;
  }
}
