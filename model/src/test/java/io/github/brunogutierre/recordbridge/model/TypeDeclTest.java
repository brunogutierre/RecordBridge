package io.github.brunogutierre.recordbridge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Tag;
import io.github.brunogutierre.recordbridge.model.TypeDecl.UnionType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Variant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeDeclTest {

  @Test
  void propertyNamesMayBeAnyJsonKey() {
    var type = ObjectType.of("Person", List.of(Property.required("first-name", TypeRef.STRING)));

    assertThat(type.properties()).extracting(Property::name).containsExactly("first-name");
  }

  @Test
  void objectRejectsDuplicatePropertiesAndTags() {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            ObjectType.of(
                "Person",
                List.of(
                    Property.required("name", TypeRef.STRING),
                    Property.required("name", TypeRef.STRING))))
        .withMessageContaining("duplicate property of Person: 'name'");
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new ObjectType(
                "Circle",
                List.of(),
                List.of(Property.required("type", TypeRef.STRING)),
                List.of(new Tag("type", "circle"))));
  }

  @Test
  void objectRejectsDuplicateTypeParameters() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ObjectType("Pair", List.of("T", "T"), List.of(), List.of()));
  }

  @Test
  void enumNeedsUniqueConstants() {
    assertThatIllegalArgumentException().isThrownBy(() -> new EnumType("Status", List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EnumType("Status", List.of("OPEN", "OPEN")));
    assertThat(new EnumType("Status", List.of("OPEN", "CLOSED")).typeParameters()).isEmpty();
  }

  @Test
  void taggedUnionNeedsDistinctTags() {
    var circle = new Variant("shape", TypeRef.Named.of("Circle"));
    var square = new Variant("shape", TypeRef.Named.of("Square"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UnionType("Shape", List.of(), "type", List.of(circle, square)))
        .withMessageContaining("duplicate tag of Shape");
  }

  @Test
  void taggedUnionNeedsATagOnEveryVariant() {
    var untagged = new Variant(null, TypeRef.Named.of("Circle"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UnionType("Shape", List.of(), "type", List.of(untagged)))
        .withMessageContaining("needs a tag");
  }

  @Test
  void deducedUnionHasNoDiscriminator() {
    var union =
        new UnionType(
            "Shape",
            List.of(),
            null,
            List.of(
                new Variant(null, TypeRef.Named.of("Circle")),
                new Variant(null, TypeRef.Named.of("Square"))));

    assertThat(union.discriminator()).isNull();
  }

  @Test
  void unionNeedsVariants() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UnionType("Shape", List.of(), "type", List.of()));
  }
}
