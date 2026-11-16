package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

/** Reads one handler method of a controller into an {@link Endpoint}. */
final class EndpointReader {

  /** Parameter types Spring resolves on the server: never sent by the client. */
  private static final Set<String> SERVER_SIDE_TYPES =
      Set.of(
          "java.security.Principal",
          "java.util.Locale",
          "java.util.TimeZone",
          "java.time.ZoneId",
          "java.io.InputStream",
          "java.io.Reader",
          "java.io.OutputStream",
          "java.io.Writer",
          "org.springframework.http.HttpMethod",
          "org.springframework.security.core.Authentication",
          "org.springframework.ui.Model",
          "org.springframework.ui.ModelMap",
          "org.springframework.validation.BindingResult",
          "org.springframework.validation.Errors",
          "org.springframework.web.bind.support.SessionStatus",
          "org.springframework.web.context.request.NativeWebRequest",
          "org.springframework.web.context.request.WebRequest",
          "org.springframework.web.servlet.mvc.support.RedirectAttributes",
          "org.springframework.web.util.UriComponentsBuilder");

  private static final Set<String> SERVER_SIDE_ANNOTATIONS =
      Set.of("AuthenticationPrincipal", "RequestAttribute", "SessionAttribute", "CookieValue");

  private static final String PAGEABLE = "org.springframework.data.domain.Pageable";

  /** Return wrappers whose first type argument is the actual response body. */
  private static final Set<String> ASYNC_WRAPPERS =
      Set.of(
          "java.util.concurrent.CompletableFuture",
          "java.util.concurrent.CompletionStage",
          "java.util.concurrent.Callable",
          "org.springframework.web.context.request.async.DeferredResult",
          "org.springframework.web.context.request.async.WebAsyncTask",
          "reactor.core.publisher.Mono");

  /** Bodies Spring writes as raw bytes. */
  private static final Set<String> BINARY_TYPES =
      Set.of(
          "org.springframework.core.io.Resource",
          "org.springframework.core.io.InputStreamResource",
          "org.springframework.core.io.ByteArrayResource",
          "org.springframework.core.io.FileSystemResource",
          "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");

  private final TypeReader types;

  EndpointReader(TypeReader types) {
    this.types = types;
  }

  /**
   * Reads a handler method.
   *
   * @param controller the controller class; it may inherit the method from a generic base class,
   *     whose type variables it binds
   * @param classMapping the controller's own {@code @RequestMapping}, if any
   */
  Endpoint read(Class<?> controller, @Nullable RequestMapping classMapping, Method method) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    String where = controller.getSimpleName() + "." + method.getName();
    if (mapping == null) {
      throw new UnsupportedTypeException(where + " has no request mapping");
    }
    var context =
        new Context(method, Generics.bindings(controller, method.getDeclaringClass()), where);
    String basePath = classMapping == null ? "" : first(classMapping.path());
    String[] produces =
        mapping.produces().length > 0 || classMapping == null
            ? mapping.produces()
            : classMapping.produces();
    return new Endpoint(
        method.getName(),
        httpMethod(mapping, where),
        join(basePath, first(mapping.path())),
        params(context),
        response(context, produces));
  }

  /** The method being read, the type variables its class binds, and where it is for errors. */
  private record Context(Method method, Map<TypeVariable<?>, Type> bindings, String where) {}

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

  private List<Param> params(Context context) {
    var params = new ArrayList<Param>();
    Parameter[] parameters = context.method().getParameters();
    // AnnotatedMethod merges parameter annotations declared on interfaces and superclasses, as
    // Spring MVC does for its handler methods.
    MethodParameter[] merged = new AnnotatedMethod(context.method()).getMethodParameters();
    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      MethodParameter annotations = merged[i];
      if (isServerSide(parameter, annotations)) {
        continue;
      }
      if (!parameter.isNamePresent()) {
        throw new UnsupportedTypeException(
            context.where() + ": parameter names are missing; compile with the -parameters flag");
      }
      String where = context.where() + "(" + parameter.getName() + ")";
      PathVariable pathVariable = annotations.getParameterAnnotation(PathVariable.class);
      RequestParam requestParam = annotations.getParameterAnnotation(RequestParam.class);
      RequestHeader header = annotations.getParameterAnnotation(RequestHeader.class);
      RequestBody body = annotations.getParameterAnnotation(RequestBody.class);
      if (pathVariable != null) {
        params.add(param(context, parameter, pathVariable.name(), Param.Kind.PATH, true, where));
      } else if (header != null) {
        boolean required = required(header.required(), header.defaultValue());
        params.add(param(context, parameter, header.name(), Param.Kind.HEADER, required, where));
      } else if (body != null) {
        // Spring reads a String body as plain text; anything else goes through Jackson.
        Param.Kind kind =
            parameter.getType() == String.class ? Param.Kind.TEXT_BODY : Param.Kind.BODY;
        params.add(param(context, parameter, "", kind, body.required(), where));
      } else if (requestParam != null) {
        boolean required = required(requestParam.required(), requestParam.defaultValue());
        params.add(
            param(context, parameter, requestParam.name(), Param.Kind.QUERY, required, where));
      } else if (PAGEABLE.equals(parameter.getType().getName())) {
        params.addAll(pageable());
      } else if (parameter.getType().isRecord()) {
        params.addAll(modelAttribute(parameter, where)); // a record bound from query parameters
      } else {
        // Spring binds an unannotated simple parameter as an optional @RequestParam.
        params.add(param(context, parameter, "", Param.Kind.QUERY, false, where));
      }
    }
    return params;
  }

  private static boolean required(boolean required, String defaultValue) {
    return required && ValueConstants.DEFAULT_NONE.equals(defaultValue);
  }

  private Param param(
      Context context,
      Parameter parameter,
      String wireName,
      Param.Kind kind,
      boolean required,
      String where) {
    TypeRef type = types.read(parameter.getAnnotatedType(), parameter, where, context.bindings());
    if (type instanceof TypeRef.OrNull orNull) {
      type = orNull.type(); // a null argument means "not sent"
      required = required && kind == Param.Kind.PATH;
    }
    if (Optional.class.isAssignableFrom(parameter.getType())) {
      required = false;
    }
    if (!kind.isBody()) {
      type = simple(types.withEnumConstantNames(type), kind, where);
    }
    String name = parameter.getName();
    return new Param(name, wireName.isEmpty() ? name : wireName, kind, type, required);
  }

  /**
   * Checks that a value can travel in a URL or a header: a scalar, an enum or, outside the path, an
   * array of them. Anything else needs {@code @RequestBody} or a record of simple components.
   */
  private TypeRef simple(TypeRef type, Param.Kind kind, String where) {
    TypeRef element =
        type instanceof TypeRef.ArrayOf array && kind != Param.Kind.PATH ? array.element() : type;
    if (element instanceof TypeRef.OrNull orNull) {
      element = orNull.type();
    }
    boolean scalar = element instanceof TypeRef.Primitive p && !p.equals(TypeRef.UNKNOWN);
    boolean enumeration = element instanceof TypeRef.Named named && types.isEnum(named.name());
    if (!scalar && !enumeration) {
      throw new UnsupportedTypeException(
          where
              + " can't be sent as a "
              + kind.name().toLowerCase(java.util.Locale.ROOT)
              + " parameter; use @RequestBody, or a record of simple values for query"
              + " parameters");
    }
    return type;
  }

  /**
   * Spring Data's {@code Pageable} is bound from the {@code page}, {@code size} and {@code sort}
   * query parameters.
   */
  private static List<Param> pageable() {
    return List.of(
        new Param("page", "page", Param.Kind.QUERY, TypeRef.NUMBER, false),
        new Param("size", "size", Param.Kind.QUERY, TypeRef.NUMBER, false),
        new Param("sort", "sort", Param.Kind.QUERY, new TypeRef.ArrayOf(TypeRef.STRING), false));
  }

  private List<Param> modelAttribute(Parameter parameter, String where) {
    var params = new ArrayList<Param>();
    for (RecordComponent component : parameter.getType().getRecordComponents()) {
      String path = where + "." + component.getName();
      TypeRef type = types.read(component.getAnnotatedType(), component.getAccessor(), path);
      if (type instanceof TypeRef.OrNull orNull) {
        type = orNull.type();
      }
      type = simple(types.withEnumConstantNames(type), Param.Kind.QUERY, path);
      params.add(
          new Param(component.getName(), component.getName(), Param.Kind.QUERY, type, false));
    }
    return params;
  }

  private static boolean isServerSide(Parameter parameter, MethodParameter merged) {
    for (Annotation annotation : merged.getParameterAnnotations()) {
      if (SERVER_SIDE_ANNOTATIONS.contains(annotation.annotationType().getSimpleName())) {
        return true;
      }
    }
    boolean forClient =
        merged.hasParameterAnnotation(RequestBody.class)
            || merged.hasParameterAnnotation(RequestParam.class)
            || merged.hasParameterAnnotation(RequestHeader.class)
            || merged.hasParameterAnnotation(PathVariable.class);
    if (forClient) {
      return false;
    }
    String type = parameter.getType().getName();
    if (SERVER_SIDE_TYPES.contains(type) || type.startsWith("jakarta.servlet.")) {
      return true;
    }
    if (type.startsWith("org.springframework.") && !type.equals(PAGEABLE)) {
      throw new UnsupportedTypeException(
          "Unsupported Spring parameter type "
              + type
              + " in "
              + parameter.getDeclaringExecutable());
    }
    return false;
  }

  private Response response(Context context, String[] produces) {
    Method method = context.method();
    String where = context.where();
    AnnotatedType returned = method.getAnnotatedReturnType();
    String returnedName = rawName(returned);
    if ("org.springframework.web.servlet.mvc.method.annotation.SseEmitter".equals(returnedName)) {
      throw new UnsupportedTypeException(
          where
              + " returns SseEmitter, which doesn't say what its events contain; return"
              + " Flux<ServerSentEvent<T>> instead");
    }
    if ("reactor.core.publisher.Flux".equals(returnedName)) {
      return flux(context, returned, produces);
    }
    AnnotatedType body = unwrap(returned);
    Type type = body.getType();
    if (type == void.class || type == Void.class) {
      return Response.EMPTY;
    }
    if (type == String.class) {
      return Response.TEXT; // StringHttpMessageConverter writes it as is, not as JSON
    }
    String bodyName = rawName(body);
    if (type == byte[].class || (bodyName != null && BINARY_TYPES.contains(bodyName))) {
      throw new UnsupportedTypeException(
          where + " returns binary content, which RecordBridge doesn't support yet");
    }
    // A null result is an empty body, which Angular reads as null.
    return new Response.Json(types.read(body, method, where + " response", context.bindings()));
  }

  /**
   * A {@code Flux} is an event stream when it emits {@code ServerSentEvent}s or the mapping
   * produces {@code text/event-stream}; otherwise Spring MVC collects it into a JSON array.
   */
  private Response flux(Context context, AnnotatedType returned, String[] produces) {
    String where = context.where();
    if (!(returned instanceof AnnotatedParameterizedType parameterized)) {
      throw new UnsupportedTypeException(where + " returns a raw Flux; declare its element type");
    }
    AnnotatedType element = parameterized.getAnnotatedActualTypeArguments()[0];
    boolean sse = "org.springframework.http.codec.ServerSentEvent".equals(rawName(element));
    if (sse) {
      if (!(element instanceof AnnotatedParameterizedType event)) {
        throw new UnsupportedTypeException(
            where + " returns raw ServerSentEvents; declare their data type");
      }
      element = event.getAnnotatedActualTypeArguments()[0];
    }
    boolean eventStream =
        sse || Arrays.stream(produces).anyMatch(p -> p.startsWith("text/event-stream"));
    TypeRef type = types.read(element, context.method(), where + " response", context.bindings());
    if (eventStream) {
      TypeRef event = type instanceof TypeRef.OrNull orNull ? orNull.type() : type;
      // Spring writes String event data as is; everything else as JSON.
      return new Response.EventStream(event, element.getType() == String.class);
    }
    return new Response.Json(new TypeRef.ArrayOf(type));
  }

  /** Strips ResponseEntity and async wrappers down to the body type. */
  static AnnotatedType unwrap(AnnotatedType type) {
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
        return PlainTypes.of(Object.class); // a raw ResponseEntity: any JSON
      }
      current = parameterized.getAnnotatedActualTypeArguments()[0];
    }
  }

  private static @Nullable String rawName(AnnotatedType type) {
    Type t = type.getType();
    if (t instanceof Class<?> c) {
      return c.getName();
    }
    if (t instanceof ParameterizedType p) {
      return ((Class<?>) p.getRawType()).getName();
    }
    return null;
  }
}
