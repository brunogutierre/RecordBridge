package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Fixtures for polymorphic types. */
@SuppressWarnings("unused")
final class UnionFixtures {

  private UnionFixtures() {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  sealed interface Shape permits Circle, Square, Polygon {}

  @JsonTypeName("circle")
  record Circle(double radius) implements Shape {}

  @JsonTypeName("square")
  record Square(double side) implements Shape {}

  sealed interface Polygon extends Shape permits Triangle {}

  @JsonTypeName("triangle")
  record Triangle(double base, double height) implements Polygon {}

  sealed interface Plain permits Text, Count {}

  record Text(String text) implements Plain {}

  record Count(int count) implements Plain {}

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Dog.class, name = "dog"),
    @JsonSubTypes.Type(value = Cat.class, name = "cat")
  })
  interface Animal {}

  record Dog(String type, String name) implements Animal {}

  record Cat(String type, int lives) implements Animal {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  sealed interface Result<T> permits Ok, Err {}

  @JsonTypeName("ok")
  record Ok<T>(T value) implements Result<T> {}

  @JsonTypeName("err")
  record Err(String message) implements Result<Object> {}

  record Uses(Result<String> result) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
  sealed interface Deduced permits ByName, ById {}

  record ByName(String name) implements Deduced {}

  record ById(long id) implements Deduced {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
  sealed interface Wrapped permits Inner {}

  record Inner(String value) implements Wrapped {}

  interface Orphan {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  sealed interface Defaults permits Anonymous {}

  record Anonymous(String value) implements Defaults {}
}
