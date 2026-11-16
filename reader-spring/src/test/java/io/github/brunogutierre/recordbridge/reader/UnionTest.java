package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Tag;
import io.github.brunogutierre.recordbridge.model.TypeDecl.UnionType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Variant;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import io.github.brunogutierre.recordbridge.model.TypeRef.Variable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UnionTest {

  private TypeReader reader;

  @BeforeEach
  void setUp() {
    reader = new TypeReader(JsonMapper.builder().build());
  }

  private TypeDecl declaration(String name) {
    return reader.declarations().stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void sealedHierarchyWithTypeInfoIsATaggedUnion() {
    assertThat(reader.read(UnionFixtures.Shape.class)).isEqualTo(Named.of("Shape"));

    assertThat(declaration("Shape"))
        .isEqualTo(
            new UnionType(
                "Shape",
                List.of(),
                "kind",
                List.of(
                    new Variant("circle", Named.of("Circle")),
                    new Variant("square", Named.of("Square")),
                    new Variant("triangle", Named.of("Triangle")))));
    assertThat(declaration("Circle"))
        .isEqualTo(
            new ObjectType(
                "Circle",
                List.of(),
                List.of(Property.required("radius", TypeRef.NUMBER)),
                List.of(new Tag("kind", "circle"))));
    assertThat(reader.declarations()).extracting(TypeDecl::name).doesNotContain("Polygon");
  }

  @Test
  void variantsKeepTheirTagWhenUsedAlone() {
    reader.read(UnionFixtures.Square.class);

    assertThat(((ObjectType) declaration("Square")).tags())
        .containsExactly(new Tag("kind", "square"));
  }

  @Test
  void sealedHierarchyWithoutTypeInfoIsAnUntaggedUnion() {
    reader.read(UnionFixtures.Plain.class);

    assertThat(declaration("Plain"))
        .isEqualTo(
            new UnionType(
                "Plain",
                List.of(),
                null,
                List.of(
                    new Variant(null, Named.of("Text")), new Variant(null, Named.of("Count")))));
    assertThat(((ObjectType) declaration("Text")).tags()).isEmpty();
  }

  @Test
  void existingPropertyBecomesTheTag() {
    reader.read(UnionFixtures.Animal.class);

    assertThat(((UnionType) declaration("Animal")).variants())
        .containsExactly(new Variant("cat", Named.of("Cat")), new Variant("dog", Named.of("Dog")));
    assertThat(declaration("Dog"))
        .isEqualTo(
            new ObjectType(
                "Dog",
                List.of(),
                List.of(Property.required("name", TypeRef.STRING)),
                List.of(new Tag("type", "dog"))));
  }

  @Test
  void genericVariantsPassTheUnionTypeParameter() {
    reader.read(UnionFixtures.Uses.class);

    assertThat(((ObjectType) declaration("Uses")).properties())
        .containsExactly(Property.required("result", new Named("Result", List.of(TypeRef.STRING))));
    assertThat(declaration("Result"))
        .isEqualTo(
            new UnionType(
                "Result",
                List.of("T"),
                "@type",
                List.of(
                    new Variant("ok", new Named("Ok", List.of(new Variable("T")))),
                    new Variant("err", Named.of("Err")))));
    assertThat(new ApiModel(reader.declarations(), List.of()).types()).hasSize(4);
  }

  @Test
  void deductionIsAnUntaggedUnion() {
    reader.read(UnionFixtures.Deduced.class);

    assertThat(((UnionType) declaration("Deduced")).discriminator()).isNull();
    assertThat(((ObjectType) declaration("ById")).tags()).isEmpty();
  }

  @Test
  void defaultTypeIdsComeFromJackson() {
    reader.read(UnionFixtures.Defaults.class);

    var variant = ((UnionType) declaration("Defaults")).variants().get(0);
    assertThat(variant.tag()).isNotBlank();
    assertThat(((ObjectType) declaration("Anonymous")).tags())
        .containsExactly(new Tag("@type", variant.tag()));
  }

  @Test
  void tagsMatchWhatJacksonWrites() {
    var mapper = JsonMapper.builder().build();
    reader.read(UnionFixtures.Shape.class);
    reader.read(UnionFixtures.Animal.class);
    reader.read(UnionFixtures.Defaults.class);

    List<Object> values =
        List.of(
            new UnionFixtures.Circle(1),
            new UnionFixtures.Triangle(1, 2),
            new UnionFixtures.Dog("dog", "Rex"),
            new UnionFixtures.Anonymous("x"));
    for (Object value : values) {
      Tag tag = ((ObjectType) declaration(value.getClass().getSimpleName())).tags().get(0);
      var json = mapper.valueToTree(value);
      assertThat(json.get(tag.property()).asString()).as(json.toString()).isEqualTo(tag.value());
    }
  }

  @Test
  void rejectsWrapperInclusion() {
    assertThatThrownBy(() -> reader.read(UnionFixtures.Wrapped.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("include = WRAPPER_OBJECT");
  }

  @Test
  void rejectsAbstractTypesWithoutSubtypes() {
    assertThatThrownBy(() -> reader.read(UnionFixtures.Orphan.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("has no known subtypes");
  }
}
