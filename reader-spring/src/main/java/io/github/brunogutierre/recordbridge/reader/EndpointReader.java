package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

/** Reads one handler method of a controller into an {@link Endpoint}. */
final class EndpointReader {

  /** Parameter types Spring resolves from the server side: never sent by the client. */
  private static final List<String> SERVER_SIDE_PACKAGES =
      List.of("jakarta.servlet.", "org.springframework.", "java.io.", "java.security.");

  private static final Set<Class<?>> SERVER_SIDE_TYPES =
      Set.of(java.util.Locale.class, java.util.TimeZone.class, java.time.ZoneId.class);

  private static final Set<String> SERVER_SIDE_ANNOTATIONS =
      Set.of("AuthenticationPrincipal", "RequestAttribute", "SessionAttribute", "CookieValue");

  /** Return wrappers whose first type argument is the actual response body. */
  private static final Set<String> ASYNC_WRAPPERS =
      Set.of(
          "java.util.concurrent.CompletableFuture",
          "java.util.concurrent.CompletionStage",
          "java.util.concurrent.Callable",
          "org.springframework.web.context.request.async.DeferredResult",
          "org.springframework.web.context.request.async.WebAsyncTask",
          "reactor.core.publisher.Mono");

  private final TypeReader types;

  EndpointReader(TypeReader types) {
    this.types = types;
  }

  Endpoint read(String basePath, Method method) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    String where = method.getDeclaringClass().getSimpleName() + "." + method.getName();
    if (mapping == null) {
      throw new UnsupportedTypeException(where + " has no request mapping");
    }
    return new Endpoint(
        method.getName(),
        httpMethod(mapping, where),
        join(basePath, first(mapping.path())),
        params(method, where),
        response(method, where));
  }

  static String first(String[] paths) {
    return paths.length == 0 ? "" : paths[0];
  }

  static String join(String base, String path) {
    String joined = ("/" + base + "/" + path).replaceAll("/+", "/");
    return joined.length() > 1 && joined.endsWith("/")
        ? joined.substring(0, joined.length() - 1)
        : joined;
  }

  private static HttpMethod httpMethod(RequestMapping mapping, String where) {
    RequestMethod[] methods = mapping.method();
    if (methods.length != 1) {
      throw new UnsupportedTypeException(
          where + " must be mapped to exactly one HTTP method, such as @GetMapping");
    }
    return switch (methods[0]) {
      case GET -> HttpMethod.GET;
      case POST -> HttpMethod.POST;
      case PUT -> HttpMethod.PUT;
      case PATCH -> HttpMethod.PATCH;
      case DELETE -> HttpMethod.DELETE;
      default -> throw new UnsupportedTypeException(where + " uses unsupported " + methods[0]);
    };
  }

  private List<Param> params(Method method, String where) {
    var params = new ArrayList<Param>();
    for (java.lang.reflect.Parameter parameter : method.getParameters()) {
      if (isServerSide(parameter)) {
        continue;
      }
      if (!parameter.isNamePresent()) {
        throw new UnsupportedTypeException(
            where + ": parameter names are missing; compile with the -parameters flag");
      }
      String path = where + "(" + parameter.getName() + ")";
      // Merged, so @AliasFor works: @PathVariable("id") sets value(), read through name().
      PathVariable pathVariable =
          AnnotatedElementUtils.findMergedAnnotation(parameter, PathVariable.class);
      RequestParam requestParam =
          AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
      RequestHeader header =
          AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
      RequestBody body = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestBody.class);
      if (pathVariable != null) {
        params.add(param(parameter, pathVariable.name(), Param.Kind.PATH, true, path));
      } else if (header != null) {
        params.add(
            param(
                parameter,
                header.name(),
                Param.Kind.HEADER,
                required(header.required(), header.defaultValue()),
                path));
      } else if (body != null) {
        params.add(param(parameter, "", Param.Kind.BODY, body.required(), path));
      } else if (requestParam != null) {
        rejectMap(parameter, path);
        params.add(
            param(
                parameter,
                requestParam.name(),
                Param.Kind.QUERY,
                required(requestParam.required(), requestParam.defaultValue()),
                path));
      } else if (parameter.getType().isRecord()) {
        params.addAll(modelAttribute(parameter, path)); // a record bound from query parameters
      } else {
        rejectMap(parameter, path);
        // Spring binds an unannotated simple parameter as an optional @RequestParam.
        params.add(param(parameter, "", Param.Kind.QUERY, false, path));
      }
    }
    return params;
  }

  private static boolean required(boolean required, String defaultValue) {
    return required && ValueConstants.DEFAULT_NONE.equals(defaultValue);
  }

  private static void rejectMap(java.lang.reflect.Parameter parameter, String where) {
    if (java.util.Map.class.isAssignableFrom(parameter.getType())) {
      throw new UnsupportedTypeException(
          where + ": a Map of all query parameters can't be typed; declare each parameter");
    }
  }

  private Param param(
      java.lang.reflect.Parameter parameter,
      String wireName,
      Param.Kind kind,
      boolean required,
      String where) {
    TypeRef type = types.read(parameter.getAnnotatedType(), parameter, where);
    boolean optional = Optional.class.isAssignableFrom(parameter.getType());
    if (type instanceof TypeRef.OrNull orNull) {
      type = orNull.type(); // a null argument means "not sent"
      required = required && kind == Param.Kind.PATH;
    }
    if (optional) {
      required = false;
    }
    String name = parameter.getName();
    return new Param(name, wireName.isEmpty() ? name : wireName, kind, type, required);
  }

  private List<Param> modelAttribute(java.lang.reflect.Parameter parameter, String where) {
    var params = new ArrayList<Param>();
    for (RecordComponent component : parameter.getType().getRecordComponents()) {
      TypeRef type =
          types.read(
              component.getAnnotatedType(),
              component.getAccessor(),
              where + "." + component.getName());
      if (type instanceof TypeRef.OrNull orNull) {
        type = orNull.type();
      }
      params.add(
          new Param(component.getName(), component.getName(), Param.Kind.QUERY, type, false));
    }
    return params;
  }

  private static boolean isServerSide(java.lang.reflect.Parameter parameter) {
    Class<?> type = parameter.getType();
    for (var annotation : parameter.getAnnotations()) {
      if (SERVER_SIDE_ANNOTATIONS.contains(annotation.annotationType().getSimpleName())) {
        return true;
      }
    }
    boolean annotatedForClient =
        parameter.isAnnotationPresent(RequestBody.class)
            || parameter.isAnnotationPresent(RequestParam.class)
            || parameter.isAnnotationPresent(RequestHeader.class)
            || parameter.isAnnotationPresent(PathVariable.class);
    return !annotatedForClient
        && (SERVER_SIDE_TYPES.contains(type)
            || SERVER_SIDE_PACKAGES.stream().anyMatch(p -> type.getName().startsWith(p)));
  }

  private Response response(Method method, String where) {
    AnnotatedType body = unwrap(method.getAnnotatedReturnType());
    java.lang.reflect.Type type = body.getType();
    if (type == void.class || type == Void.class) {
      return Response.EMPTY;
    }
    if (type == String.class) {
      return Response.TEXT; // StringHttpMessageConverter writes it as is, not as JSON
    }
    TypeRef ref = types.read(body, method, where + " response");
    return new Response.Json(ref instanceof TypeRef.OrNull orNull ? orNull.type() : ref);
  }

  /** Strips ResponseEntity and async wrappers down to the body type. */
  private static AnnotatedType unwrap(AnnotatedType type) {
    AnnotatedType current = type;
    while (true) {
      @Nullable String raw = rawName(current);
      boolean wrapper =
          "org.springframework.http.ResponseEntity".equals(raw)
              || "org.springframework.http.HttpEntity".equals(raw)
              || (raw != null && ASYNC_WRAPPERS.contains(raw));
      if (!wrapper) {
        return current;
      }
      if (!(current instanceof AnnotatedParameterizedType parameterized)) {
        return new UnknownBody();
      }
      current = parameterized.getAnnotatedActualTypeArguments()[0];
    }
  }

  private static @Nullable String rawName(AnnotatedType type) {
    java.lang.reflect.Type t = type.getType();
    if (t instanceof Class<?> c) {
      return c.getName();
    }
    if (t instanceof ParameterizedType p) {
      return ((Class<?>) p.getRawType()).getName();
    }
    return null;
  }

  /** The body of a raw {@code ResponseEntity}: any JSON. */
  private static final class UnknownBody implements AnnotatedType {
    @Override
    public java.lang.reflect.Type getType() {
      return Object.class;
    }

    @Override
    public <T extends java.lang.annotation.Annotation> @Nullable T getAnnotation(
        Class<T> annotationClass) {
      return null;
    }

    @Override
    public java.lang.annotation.Annotation[] getAnnotations() {
      return new java.lang.annotation.Annotation[0];
    }

    @Override
    public java.lang.annotation.Annotation[] getDeclaredAnnotations() {
      return getAnnotations();
    }
  }
}
