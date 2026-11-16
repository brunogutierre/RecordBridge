package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;

/** Jackson annotations that change which properties are written. */
@SuppressWarnings("unused")
final class PropertyFixtures {

  private PropertyFixtures() {}

  @JsonIgnoreProperties({"secret"})
  record Account(String name, String secret) {}

  @JsonIncludeProperties({"id"})
  record Narrow(long id, String internal) {}

  record Address(String street, String city) {}

  record Customer(String name, @JsonUnwrapped(prefix = "home_") Address address) {}

  record Signup(
      String email, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {}

  /** A bean with a write-only setter. */
  static final class Login {
    private String user = "";

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public void setPassword(String password) {}
  }

  @JsonIgnoreType
  record Internal(String value) {}

  record Holder(String name, Internal internal) {}

  record Formatted(@JsonFormat(shape = JsonFormat.Shape.STRING) long id, long count) {}

  static final class Dynamic {
    public String getName() {
      return "";
    }

    @JsonAnyGetter
    public Map<String, Object> extra() {
      return Map.of();
    }
  }
}
