package io.github.brunogutierre.recordbridge.model;

/**
 * A parameter of an endpoint.
 *
 * @param name the name used in the generated code
 * @param wireName the name used in the request: path variable, query parameter or header name
 * @param kind where the value goes in the request
 * @param type the parameter type
 * @param required whether the caller must provide a value
 */
public record Param(String name, String wireName, Kind kind, TypeRef type, boolean required) {

  /** Where a parameter goes in the HTTP request. */
  public enum Kind {
    PATH,
    QUERY,
    HEADER,
    BODY
  }

  public Param {
    Names.requireIdentifier(name, "parameter name");
    Names.requireNotBlank(wireName, "parameter wire name");
    if (kind == Kind.PATH && !required) {
      throw new IllegalArgumentException("path parameter " + name + " must be required");
    }
  }
}
