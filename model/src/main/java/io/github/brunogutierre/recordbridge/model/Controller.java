package io.github.brunogutierre.recordbridge.model;

import java.util.List;

/**
 * A group of endpoints, read from one {@code @RestController}; it becomes one Angular service.
 *
 * @param name the service name, such as {@code OrderApi}
 */
public record Controller(String name, List<Endpoint> endpoints) {
  public Controller {
    Names.requireIdentifier(name, "controller name");
    endpoints = List.copyOf(endpoints);
    Names.requireUnique(endpoints, Endpoint::name, "endpoint of " + name);
  }
}
