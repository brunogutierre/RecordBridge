package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.MapOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import io.github.brunogutierre.recordbridge.model.TypeRef.Variable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TypeReaderTest {

  private TypeReader reader;

  @BeforeEach
  void setUp() {
    reader = new TypeReader(JsonMapper.builder().build());
  }

  private ObjectType object(String name) {
    return (ObjectType)
        reader.declarations().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
  }

  private TypeRef field(String name) throws NoSuchFieldException {
    var field = Fixtures.Uses.class.getDeclaredField(name);
    return reader.read(field.getAnnotatedType(), field, "Uses." + name);
  }

  @Test
  void readsScalars() {
    assertThat(reader.read(Fixtures.Primitives.class)).isEqualTo(Named.of("Primitives"));

    assertThat(object("Primitives").properties())
        .containsExactly(
            Property.required("text", TypeRef.STRING),
            Property.required("letter", TypeRef.STRING),
            Property.required("count", TypeRef.NUMBER),
            Property.required("total", TypeRef.NUMBER),
            Property.required("ratio", TypeRef.NUMBER),
            Property.required("price", TypeRef.NUMBER),
            Property.required("active", TypeRef.BOOLEAN),
            Property.required("id", TypeRef.STRING),
            Property.required("data", TypeRef.STRING),
            Property.required("anything", TypeRef.UNKNOWN),
            Property.required("tree", TypeRef.UNKNOWN));
  }

  @Test
  void readsEnumsAsTheirJsonValues() {
    reader.read(Fixtures.Status.class);
    reader.read(Fixtures.Priority.class);
    reader.read(Fixtures.Color.class);

    assertThat(reader.declarations())
        .containsExactly(
            new EnumType("Status", List.of("OPEN", "CLOSED")),
            new EnumType("Priority", List.of("low", "high")),
            new EnumType("Color", List.of("#f00", "#0f0")));
  }

  @Test
  void readsCollectionsAndMaps() {
    reader.read(Fixtures.Order.class);

    var line = Named.of("Line");
    assertThat(object("Order").properties())
        .extracting(Property::type)
        .containsExactly(
            TypeRef.NUMBER,
            Named.of("Status"),
            new ArrayOf(line),
            new ArrayOf(TypeRef.STRING),
            new ArrayOf(line),
            new MapOf(TypeRef.STRING, line),
            new MapOf(Named.of("Status"), TypeRef.NUMBER),
            new MapOf(TypeRef.STRING, TypeRef.STRING));
    assertThat(reader.declarations())
        .extracting(TypeDecl::name)
        .containsExactlyInAnyOrder("Order", "Status", "Line");
  }

  @Test
  void followsJacksonNamesForBeans() {
    reader.read(Fixtures.Customer.class);

    assertThat(object("Customer").properties())
        .extracting(Property::name)
        .containsExactlyInAnyOrder("name", "e-mail");
  }

  @Test
  void keepsTypeVariablesOfGenericTypes() throws Exception {
    assertThat(field("orderPage")).isEqualTo(new Named("Page", List.of(Named.of("Order"))));

    ObjectType page = object("Page");
    assertThat(page.typeParameters()).containsExactly("T");
    assertThat(page.properties())
        .containsExactly(
            Property.required("items", new ArrayOf(new Variable("T"))),
            Property.required("total", TypeRef.NUMBER));
  }

  @Test
  void readsNestedTypeArguments() throws Exception {
    assertThat(field("nested"))
        .isEqualTo(
            new Named(
                "Pair", List.of(TypeRef.STRING, new Named("Page", List.of(Named.of("Line"))))));
    assertThat(field("matrix")).isEqualTo(new ArrayOf(new ArrayOf(TypeRef.STRING)));
  }

  @Test
  void readsWildcardsAndRawTypes() throws Exception {
    assertThat(field("numbers")).isEqualTo(new ArrayOf(TypeRef.NUMBER));
    assertThat(field("sinks")).isEqualTo(new ArrayOf(TypeRef.UNKNOWN));
    assertThat(field("rawPage")).isEqualTo(new Named("Page", List.of(TypeRef.UNKNOWN)));
    assertThat(field("rawList")).isEqualTo(new ArrayOf(TypeRef.UNKNOWN));
    assertThat(field("rawMap")).isEqualTo(new MapOf(TypeRef.STRING, TypeRef.UNKNOWN));
  }

  @Test
  void readsRecursiveTypes() {
    reader.read(Fixtures.Node.class);

    assertThat(object("Node").properties())
        .contains(Property.required("children", new ArrayOf(Named.of("Node"))));
  }

  @Test
  void writesValueClassesAsTheirValue() {
    reader.read(Fixtures.Contact.class);

    assertThat(object("Contact").properties())
        .containsExactly(Property.required("email", TypeRef.STRING));
    assertThat(reader.declarations()).extracting(TypeDecl::name).containsExactly("Contact");
  }

  @Test
  void declarationsFormAClosedModel() throws Exception {
    reader.read(Fixtures.Order.class);
    field("nested");
    reader.read(Fixtures.Node.class);

    assertThat(new ApiModel(reader.declarations(), List.of()).types()).hasSize(6);
  }

  @Test
  void rejectsUnknownJdkTypesWithTheirLocation() {
    assertThatThrownBy(() -> reader.read(Fixtures.Event.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessage("Unsupported JDK type java.lang.Thread at Event.at");
  }

  @Test
  void rejectsTwoClassesWithTheSameName() {
    assertThatThrownBy(() -> reader.read(Fixtures.Clash.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("Two classes map to the TypeScript name Item");
  }
}
