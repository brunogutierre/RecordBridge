package io.github.brunogutierre.recordbridge.model;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * One HTTP operation of a controller.
 *
 * @param name the method name used in the generated service
 * @param method the HTTP method
 * @param path the full path template, such as {@code /api/orders/{id}}
 * @param params the parameters, in declaration order
 * @param response what the endpoint returns on success
 */
public record Endpoint(
    String name, HttpMethod method, String path, List<Param> params, Response response) {

  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{\\*?([^}:]+)(?::[^}]*)?}");

  public Endpoint {
    Names.requireIdentifier(name, "endpoint name");
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException("path of " + name + " must start with '/': " + path);
    }
    params = List.copyOf(params);
    Names.requireUnique(params, Param::name, "parameter of " + name);
    if (params.stream().filter(p -> p.kind() == Param.Kind.BODY).count() > 1) {
      throw new IllegalArgumentException(name + " has more than one body parameter");
    }
    var declared = new TreeSet<String>();
    params.stream()
        .filter(p -> p.kind() == Param.Kind.PATH)
        .forEach(p -> declared.add(p.wireName()));
    var inPath = pathVariables(path);
    if (!declared.equals(inPath)) {
      throw new IllegalArgumentException(
          "path variables of "
              + name
              + " "
              + inPath
              + " do not match its path parameters "
              + declared);
    }
  }

  /** The variable names in a Spring path template, such as {@code id} in {@code /orders/{id}}. */
  public static Set<String> pathVariables(String path) {
    var names = new TreeSet<String>();
    var matcher = PATH_VARIABLE.matcher(path);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }
}
