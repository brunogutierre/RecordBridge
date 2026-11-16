package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Java types the reader is tested against, one per feature. */
@SuppressWarnings("unused")
final class Fixtures {

  private Fixtures() {}

  record Primitives(
      String text,
      char letter,
      int count,
      Long total,
      double ratio,
      BigDecimal price,
      boolean active,
      UUID id,
      byte[] data,
      Object anything,
      JsonNode tree) {}

  enum Status {
    OPEN,
    CLOSED
  }

  enum Priority {
    @JsonProperty("low")
    LOW,
    @JsonProperty("high")
    HIGH
  }

  enum Color {
    RED("#f00"),
    GREEN("#0f0");

    private final String hex;

    Color(String hex) {
      this.hex = hex;
    }

    @JsonValue
    String hex() {
      return hex;
    }
  }

  record Line(String product, int quantity) {}

  record Order(
      long id,
      Status status,
      List<Line> lines,
      Set<String> tags,
      Line[] history,
      Map<String, Line> byProduct,
      Map<Status, Integer> countByStatus,
      Map<Long, String> notes) {}

  /** A JavaBean: Jackson finds getters, renames and ignores. */
  static final class Customer {
    public String getName() {
      return "";
    }

    @JsonProperty("e-mail")
    public String getEmail() {
      return "";
    }

    @JsonIgnore
    public String getPassword() {
      return "";
    }
  }

  record Page<T>(List<T> items, int total) {}

  record Pair<A, B>(A first, B second) {}

  record Node(String name, List<Node> children) {}

  record Email(@JsonValue String value) {}

  record Contact(Email email) {}

  /** Holds the generic and wildcard types under test as field types. */
  static final class Uses {
    Page<Order> orderPage;
    Pair<String, Page<Line>> nested;
    List<? extends Number> numbers;
    List<? super Integer> sinks;

    @SuppressWarnings("rawtypes")
    Page rawPage;

    @SuppressWarnings("rawtypes")
    List rawList;

    @SuppressWarnings("rawtypes")
    Map rawMap;

    List<List<String>> matrix;
  }

  interface Shape {}

  record Event(Thread at) {}

  static final class First {
    record Item(String a) {}
  }

  static final class Second {
    record Item(String b) {}
  }

  record Clash(First.Item first, Second.Item second) {}
}
