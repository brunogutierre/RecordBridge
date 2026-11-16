package io.github.brunogutierre.recordbridge.emitter;

import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\*?)([^}:]+)(?::[^}]*)?}");

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
    for (Endpoint endpoint : controller.endpoints()) {
      body.append('\n').append(method(endpoint, models, helpers));
    }
    var out = new StringBuilder(ModelsEmitter.HEADER).append('\n');
    out.append("import { HttpClient } from '@angular/common/http';\n");
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

  private static String method(Endpoint endpoint, Set<String> models, Set<String> helpers) {
    var path = new ArrayList<Param>();
    @Nullable Param body = null;
    var options = new ArrayList<Param>();
    for (Param param : endpoint.params()) {
      switch (param.kind()) {
        case PATH -> path.add(param);
        case BODY -> body = param;
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
    if (body != null) {
      requestOptions.add("body: " + safe(body.name()));
    }
    List<Param> query = options.stream().filter(p -> p.kind() == Param.Kind.QUERY).toList();
    List<Param> headers = options.stream().filter(p -> p.kind() == Param.Kind.HEADER).toList();
    if (!query.isEmpty()) {
      helpers.add("httpParams");
      requestOptions.add("params: httpParams(" + values(query, optionsName) + ")");
    }
    if (!headers.isEmpty()) {
      helpers.add("httpHeaders");
      requestOptions.add("headers: httpHeaders(" + values(headers, optionsName) + ")");
    }
    if (endpoint.response() instanceof Response.Text) {
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
    out.append("    return this.http.request")
        .append(endpoint.response() instanceof Response.Text ? "" : "<" + result + ">")
        .append("('")
        .append(endpoint.method())
        .append("', ")
        .append(url(endpoint.path(), endpoint.params()));
    if (!requestOptions.isEmpty()) {
      out.append(", {\n");
      requestOptions.forEach(o -> out.append("      ").append(o).append(",\n"));
      out.append("    }");
    }
    out.append(");\n  }\n");
    return out.toString();
  }

  private static String resultType(Response response, Set<String> models) {
    if (response instanceof Response.Json json) {
      collect(json.body(), models);
      return Ts.type(json.body());
    }
    if (response instanceof Response.Text) {
      return "string";
    }
    if (response instanceof Response.EventStream) {
      throw new IllegalArgumentException("event streams are not supported yet");
    }
    return "void";
  }

  /** The URL template literal, with each path variable encoded. */
  private static String url(String path, List<Param> params) {
    Matcher matcher = PATH_VARIABLE.matcher(path);
    var out = new StringBuilder("`${this.baseUrl}");
    int last = 0;
    while (matcher.find()) {
      out.append(path, last, matcher.start());
      String wireName = matcher.group(2);
      String name =
          params.stream()
              .filter(p -> p.kind() == Param.Kind.PATH && p.wireName().equals(wireName))
              .findFirst()
              .orElseThrow()
              .name();
      // A catch-all {*rest} keeps its slashes.
      String encode = matcher.group(1).isEmpty() ? "encodeURIComponent" : "encodeURI";
      out.append("${").append(encode).append("(String(").append(safe(name)).append("))}");
      last = matcher.end();
    }
    return out.append(path.substring(last)).append('`').toString();
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
