package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PropertyTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  private ObjectType read(Class<?> type) {
    var reader = new TypeReader(mapper);
    reader.read(type);
    return (ObjectType)
        reader.declarations().stream()
            .filter(d -> d.name().equals(type.getSimpleName()))
            .findFirst()
            .orElseThrow();
  }

  /** The property names the reader declares are the keys Jackson actually writes. */
  private void assertMatchesJackson(Object value) {
    List<String> declared =
        read(value.getClass()).properties().stream()
            .filter(p -> !p.optional())
            .map(Property::name)
            .toList();
    var written = new ArrayList<String>();
    mapper.valueToTree(value).propertyNames().forEach(written::add);
    assertThat(declared).containsExactlyInAnyOrderElementsOf(written);
  }

  @Test
  void honorsIgnoredAndIncludedProperties() {
    assertThat(read(PropertyFixtures.Account.class).properties())
        .containsExactly(Property.required("name", TypeRef.STRING));
    assertThat(read(PropertyFixtures.Narrow.class).properties())
        .containsExactly(Property.required("id", TypeRef.NUMBER));
    assertMatchesJackson(new PropertyFixtures.Account("a", "b"));
    assertMatchesJackson(new PropertyFixtures.Narrow(1, "x"));
  }

  @Test
  void flattensUnwrappedProperties() {
    assertThat(read(PropertyFixtures.Customer.class).properties())
        .containsExactly(
            Property.required("name", TypeRef.STRING),
            Property.required("home_street", TypeRef.STRING),
            Property.required("home_city", TypeRef.STRING));
    assertMatchesJackson(
        new PropertyFixtures.Customer("n", new PropertyFixtures.Address("s", "c")));
  }

  @Test
  void keepsWriteOnlyPropertiesAsOptional() {
    assertThat(read(PropertyFixtures.Signup.class).properties())
        .containsExactly(
            Property.required("email", TypeRef.STRING),
            new Property("password", TypeRef.STRING, true));
    assertThat(read(PropertyFixtures.Login.class).properties())
        .containsExactlyInAnyOrder(
            Property.required("user", TypeRef.STRING),
            new Property("password", TypeRef.STRING, true));
    assertMatchesJackson(new PropertyFixtures.Signup("e", "p"));
  }

  @Test
  void skipsIgnoredTypes() {
    var reader = new TypeReader(mapper);
    reader.read(PropertyFixtures.Holder.class);

    assertThat(reader.declarations()).extracting(TypeDecl::name).containsExactly("Holder");
    assertMatchesJackson(new PropertyFixtures.Holder("h", new PropertyFixtures.Internal("i")));
  }

  @Test
  void stringShapeWritesNumbersAsStrings() {
    assertThat(read(PropertyFixtures.Formatted.class).properties())
        .containsExactly(
            Property.required("id", TypeRef.STRING), Property.required("count", TypeRef.NUMBER));
    assertThat(mapper.valueToTree(new PropertyFixtures.Formatted(1, 2)).get("id").isString())
        .isTrue();
  }

  @Test
  void rejectsAnyGetters() {
    assertThatThrownBy(() -> read(PropertyFixtures.Dynamic.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("@JsonAnyGetter");
  }
}
