package io.github.brunogutierre.recordbridge.emitter;

import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.PathTemplate;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Writes one Angular service per controller.
 *
 * <p>Each endpoint becomes a method that returns an {@code Observable}. Path variables and the body
 * are positional arguments; query parameters and headers go in one {@code params} object, which is
 * optional when all of them are.
 */
public final class ServiceEmitter {

  private final Set<String> enums;

  /** An emitter for a model without enums. */
  public ServiceEmitter() {
    this(Set.of());
  }

  /**
   * An emitter that knows which named types are enums: a JSON body whose TypeScript type is a
   * string (an enum, a UUID, a date) must be sent as JSON, not as plain text.
   */
  public ServiceEmitter(Set<String> enums) {
    this.enums = Set.copyOf(enums);
  }

  /** Words that can't name a parameter in strict-mode TypeScript. */
  private static final Set<String> RESERVED =
      Set.of(
          "arguments",
          "await",
          "break",
          "case",
          "catch",
          "class",
          "const",
          "continue",
          "debugger",
          "default",
          "delete",
          "do",
          "else",
          "enum",
          "eval",
          "export",
          "extends",
          "false",
          "finally",
          "for",
          "function",
          "if",
          "implements",
          "import",
          "in",
          "instanceof",
          "interface",
          "let",
          "new",
          "null",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "static",
          "super",
          "switch",
          "this",
          "throw",
          "true",
          "try",
          "typeof",
          "var",
          "void",
          "while",
          "with",
          "yield");

  /** The file name of a service: {@code OrderLineApi} → {@code order-line.api.ts}. */
  public static String fileName(Controller controller) {
    String base = controller.name().replaceFirst("Api$", "");
    return base.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT)
        + ".api.ts";
  }

  /** Renders the service module of a controller. */
  public String emit(Controller controller) {
    var models = new TreeSet<String>();
    var helpers = new TreeSet<String>(Set.of("API_BASE_URL"));
    var body = new StringBuilder();
    var methodNames = new java.util.HashSet<String>();
    controller.endpoints().forEach(e -> methodNames.add(e.name()));
    boolean resources = false;
    for (Endpoint endpoint : controller.endpoints()) {
      body.append('\n').append(method(endpoint, models, helpers));
      if (hasResource(endpoint)) {
        if (!methodNames.add(endpoint.name() + "Resource")) {
          throw new IllegalArgumentException(
              controller.name() + "." + endpoint.name() + "Resource clashes with an endpoint");
        }
        body.append('\n').append(resource(endpoint, helpers));
        resources = true;
      }
    }
    var out = new StringBuilder(ModelsEmitter.HEADER).append('\n');
    out.append(
        resources
            ? "import { HttpClient, httpResource, type HttpResourceOptions, type HttpResourceRef } from '@angular/common/http';\n"
            : "import { HttpClient } from '@angular/common/http';\n");
    out.append("import { Injectable, inject } from '@angular/core';\n");
    out.append("import type { Observable } from 'rxjs';\n");
    out.append("import { ").append(String.join(", ", helpers)).append(" } from './api-config';\n");
    if (!models.isEmpty()) {
      out.append("import type { ")
          .append(String.join(", ", models))
          .append(" } from './models';\n");
    }
    out.append("\n@Injectable({ providedIn: 'root' })\n")
        .append("export class ")
        .append(controller.name())
        .append(" {\n")
        .append("  private readonly http = inject(HttpClient);\n")
        .append("  private readonly baseUrl = inject(API_BASE_URL);\n")
        .append(body)
        .append("}\n");
    return out.toString();
  }

  private String method(Endpoint endpoint, Set<String> models, Set<String> helpers) {
    var path = new ArrayList<Param>();
    @Nullable Param body = null;
    var options = new ArrayList<Param>();
    for (Param param : endpoint.params()) {
      switch (param.kind()) {
        case PATH -> path.add(param);
        case BODY, TEXT_BODY -> body = param;
        case QUERY, HEADER -> options.add(param);
      }
      collect(param.type(), models);
    }
    String result = resultType(endpoint.response(), models);
    boolean optionsRequired = options.stream().anyMatch(Param::required);
    String optionsName = unique("params", path, body);

    var arguments = new ArrayList<String>();
    path.forEach(p -> arguments.add(safe(p.name()) + ": " + Ts.type(p.type())));
    if (body != null) {
      String type = Ts.type(body.type());
      if (body.required()) {
        arguments.add(safe(body.name()) + ": " + type);
      } else if (optionsRequired) {
        arguments.add(safe(body.name()) + ": " + type + " | undefined");
      } else {
        arguments.add(safe(body.name()) + "?: " + type);
      }
    }
    if (!options.isEmpty()) {
      String fields =
          options.stream()
              .map(p -> Ts.key(p.name()) + (p.required() ? ": " : "?: ") + Ts.type(p.type()))
              .collect(Collectors.joining("; ", "{ ", " }"));
      arguments.add(optionsName + ": " + fields + (optionsRequired ? "" : " = {}"));
    }

    var requestOptions = new ArrayList<String>();
    var headerValues = new ArrayList<String>();
    if (body != null) {
      // Angular sends a string body as text/plain, which only Spring's String converter reads.
      boolean jsonString = body.kind() == Param.Kind.BODY && isString(body.type());
      if (jsonString) {
        requestOptions.add("body: JSON.stringify(" + safe(body.name()) + ")");
        headerValues.add("'Content-Type': 'application/json'");
      } else {
        requestOptions.add("body: " + safe(body.name()));
      }
    }
    List<Param> query = options.stream().filter(p -> p.kind() == Param.Kind.QUERY).toList();
    options.stream()
        .filter(p -> p.kind() == Param.Kind.HEADER)
        .forEach(p -> headerValues.add(Ts.key(p.wireName()) + ": " + optionsName + "." + p.name()));
    if (!query.isEmpty()) {
      helpers.add("httpParams");
      requestOptions.add("params: httpParams(" + values(query, optionsName) + ")");
    }
    if (!headerValues.isEmpty()) {
      helpers.add("httpHeaders");
      requestOptions.add("headers: httpHeaders({ " + String.join(", ", headerValues) + " })");
    }
    boolean stream = endpoint.response() instanceof Response.EventStream;
    if (stream) {
      helpers.add("sseEvents");
      requestOptions.add("observe: 'events'");
      requestOptions.add("reportProgress: true");
    }
    if (endpoint.response() instanceof Response.Text || stream) {
      requestOptions.add("responseType: 'text'");
    }

    var out = new StringBuilder();
    out.append("  /** ")
        .append(endpoint.method())
        .append(' ')
        .append(endpoint.path())
        .append(" */\n");
    out.append("  ")
        .append(endpoint.name())
        .append('(')
        .append(String.join(", ", arguments))
        .append("): Observable<")
        .append(result)
        .append("> {\n");
    out.append(
            stream
                ? "    return sseEvents<" + result + ">(this.http.request"
                : "    return this.http.request")
        .append(endpoint.response() instanceof Response.Text || stream ? "" : "<" + result + ">")
        .append("('")
        .append(endpoint.method())
        .append("', ")
        .append(url(endpoint.path(), endpoint.params(), p -> safe(p.name())));
    if (!requestOptions.isEmpty()) {
      out.append(", {\n");
      requestOptions.forEach(o -> out.append("      ").append(o).append(",\n"));
      out.append("    }");
    }
    boolean textStream =
        endpoint.response() instanceof Response.EventStream events && events.text();
    out.append(stream ? (textStream ? "), false);\n  }\n" : "));\n  }\n") : ");\n  }\n");
    return out.toString();
  }

  /** Whether a type is written as a JSON string: a string or an enum, possibly nullable. */
  private boolean isString(TypeRef type) {
    TypeRef base = type instanceof TypeRef.OrNull orNull ? orNull.type() : type;
    return base.equals(TypeRef.STRING)
        || (base instanceof TypeRef.Named named && enums.contains(named.name()));
  }

  /** GETs that return JSON or text, without a body, also get a reactive resource factory. */
  private static boolean hasResource(Endpoint endpoint) {
    return endpoint.method() == HttpMethod.GET
        && (endpoint.response() instanceof Response.Json
            || endpoint.response() instanceof Response.Text)
        && endpoint.params().stream().noneMatch(p -> p.kind().isBody());
  }

  /**
   * A factory around Angular's {@code httpResource}: every argument comes from one {@code request}
   * function, so the resource reloads when the signals it reads change, and skips the request while
   * it returns {@code undefined}.
   */
  private static String resource(Endpoint endpoint, Set<String> helpers) {
    boolean text = endpoint.response() instanceof Response.Text;
    String result = text ? "string" : Ts.type(((Response.Json) endpoint.response()).body());
    List<Param> params = endpoint.params();
    var out = new StringBuilder();
    out.append("  /** ")
        .append(endpoint.method())
        .append(' ')
        .append(endpoint.path())
        .append(", as a resource that reloads when the signals read by `request` change. */\n");
    out.append("  ").append(endpoint.name()).append("Resource(");
    if (!params.isEmpty()) {
      boolean required = params.stream().anyMatch(Param::required);
      String fields =
          params.stream()
              .map(p -> Ts.key(p.name()) + (p.required() ? ": " : "?: ") + Ts.type(p.type()))
              .collect(Collectors.joining("; ", "{ ", " }"));
      out.append("request: () => ").append(fields).append(" | undefined");
      out.append(required ? ", " : " = () => ({}), ");
    }
    out.append("options?: HttpResourceOptions<")
        .append(result)
        .append(", unknown>): HttpResourceRef<")
        .append(result)
        .append(" | undefined> {\n");
    String call = text ? "httpResource.text" : "httpResource<" + result + ">";
    var request = new ArrayList<String>();
    request.add("url: " + url(endpoint.path(), params, p -> "args." + p.name()));
    List<Param> query = params.stream().filter(p -> p.kind() == Param.Kind.QUERY).toList();
    List<Param> headers = params.stream().filter(p -> p.kind() == Param.Kind.HEADER).toList();
    if (!query.isEmpty()) {
      helpers.add("httpParams");
      request.add("params: httpParams(" + values(query, "args") + ")");
    }
    if (!headers.isEmpty()) {
      helpers.add("httpHeaders");
      request.add("headers: httpHeaders(" + values(headers, "args") + ")");
    }
    if (params.isEmpty()) {
      out.append("    return ")
          .append(call)
          .append("(() => ({ ")
          .append(request.get(0))
          .append(" }), options);\n");
    } else {
      out.append("    return ").append(call).append("(() => {\n");
      out.append("      const args = request();\n");
      out.append("      if (args === undefined) {\n");
      out.append("        return undefined;\n");
      out.append("      }\n");
      out.append("      return {\n");
      request.forEach(r -> out.append("        ").append(r).append(",\n"));
      out.append("      };\n");
      out.append("    }, options);\n");
    }
    return out.append("  }\n").toString();
  }

  private static String resultType(Response response, Set<String> models) {
    if (response instanceof Response.Json json) {
      collect(json.body(), models);
      return Ts.type(json.body());
    }
    if (response instanceof Response.Text) {
      return "string";
    }
    if (response instanceof Response.EventStream stream) {
      collect(stream.event(), models);
      return Ts.type(stream.event());
    }
    return "void";
  }

  /** The URL template literal, with each path variable encoded and the literal text escaped. */
  private static String url(String path, List<Param> params, Function<Param, String> value) {
    var out = new StringBuilder("`${this.baseUrl}");
    for (PathTemplate.Part part : PathTemplate.parse(path)) {
      out.append(templateText(part.literal()));
      String variable = part.variable();
      if (variable == null) {
        continue;
      }
      Param param =
          params.stream()
              .filter(p -> p.kind() == Param.Kind.PATH && p.wireName().equals(variable))
              .findFirst()
              .orElseThrow();
      // A catch-all {*rest} keeps its slashes.
      String encode = part.catchAll() ? "encodeURI" : "encodeURIComponent";
      out.append("${").append(encode).append("(String(").append(value.apply(param)).append("))}");
    }
    return out.append('`').toString();
  }

  /** Escapes text for a template literal: backslashes, backticks and {@code ${}. */
  static String templateText(String text) {
    return text.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${");
  }

  private static String values(List<Param> params, String optionsName) {
    return params.stream()
        .map(p -> Ts.key(p.wireName()) + ": " + optionsName + "." + p.name())
        .collect(Collectors.joining(", ", "{ ", " }"));
  }

  /** Collects the model types a reference needs to import. */
  private static void collect(TypeRef ref, Set<String> models) {
    if (ref instanceof TypeRef.Named named) {
      models.add(named.name());
      named.arguments().forEach(a -> collect(a, models));
    } else if (ref instanceof TypeRef.ArrayOf array) {
      collect(array.element(), models);
    } else if (ref instanceof TypeRef.MapOf map) {
      collect(map.key(), models);
      collect(map.value(), models);
    } else if (ref instanceof TypeRef.OrNull orNull) {
      collect(orNull.type(), models);
    }
  }

  static String safe(String name) {
    return RESERVED.contains(name) ? name + "_" : name;
  }

  /** A name for the options argument that no positional argument already uses. */
  private static String unique(String name, List<Param> path, @Nullable Param body) {
    var taken = new java.util.HashSet<String>();
    path.forEach(p -> taken.add(safe(p.name())));
    if (body != null) {
      taken.add(safe(body.name()));
    }
    String candidate = name;
    while (taken.contains(candidate)) {
      candidate = candidate + "_";
    }
    return candidate;
  }
}
