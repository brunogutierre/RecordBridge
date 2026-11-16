package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import io.github.brunogutierre.recordbridge.model.TypeRef.OrNull;
import io.github.brunogutierre.recordbridge.model.TypeRef.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class NullnessTest {

  private static ObjectType read(TypeReader reader, Class<?> type) {
    reader.read(type);
    return (ObjectType)
        reader.declarations().stream()
            .filter(d -> d.name().equals(type.getSimpleName()))
            .findFirst()
            .orElseThrow();
  }

  private static ObjectType read(Class<?> type) {
    return read(new TypeReader(JsonMapper.builder().build()), type);
  }

  @Test
  void nullMarkedTypesAreNonNullUnlessAnnotated() {
    assertThat(read(NullnessFixtures.Marked.class).properties())
        .containsExactly(
            Property.required("name", TypeRef.STRING),
            Property.required("nickname", new OrNull(TypeRef.STRING)),
            Property.required("aliases", new ArrayOf(new OrNull(TypeRef.STRING))),
            Property.required("middle", new OrNull(TypeRef.STRING)),
            Property.required("age", TypeRef.NUMBER),
            Property.required("score", new OrNull(TypeRef.NUMBER)));
  }

  @Test
  void unmarkedTypesFollowTheConfiguredDefault() {
    var lenient = read(NullnessFixtures.Unmarked.class);
    var strict =
        read(
            new TypeReader(JsonMapper.builder().build(), UnmarkedNullness.NULLABLE),
            NullnessFixtures.Unmarked.class);

    assertThat(lenient.properties())
        .extracting(Property::type)
        .containsExactly(
            TypeRef.STRING, new OrNull(TypeRef.STRING), TypeRef.STRING, TypeRef.NUMBER);
    assertThat(strict.properties())
        .extracting(Property::type)
        .containsExactly(
            new OrNull(TypeRef.STRING), new OrNull(TypeRef.STRING), TypeRef.STRING, TypeRef.NUMBER);
  }

  @Test
  void recognizesOtherNullableAnnotationsByName() {
    assertThat(read(NullnessFixtures.LegacyBean.class).properties())
        .containsExactly(Property.required("note", new OrNull(TypeRef.STRING)));
  }

  @Test
  void nonNullInclusionLeavesNullsOut() {
    assertThat(read(NullnessFixtures.Sparse.class).properties())
        .containsExactly(
            new Property("nickname", TypeRef.STRING, true),
            Property.required("middle", new OrNull(TypeRef.STRING)),
            Property.required("name", TypeRef.STRING));
  }

  @Test
  void nonAbsentInclusionLeavesEmptyOptionalsOut() {
    assertThat(read(NullnessFixtures.Absent.class).properties())
        .containsExactly(
            new Property("nickname", TypeRef.STRING, true),
            new Property("middle", TypeRef.STRING, true));
  }

  @Test
  void propertyInclusionOverridesTheClass() {
    assertThat(read(NullnessFixtures.PerProperty.class).properties())
        .containsExactly(
            new Property("tags", new ArrayOf(TypeRef.STRING), true),
            Property.required("name", TypeRef.STRING));
  }

  @Test
  void typeVariablesTakeTheirNullnessFromTheArgument() {
    var reader = new TypeReader(JsonMapper.builder().build());
    var uses = read(reader, NullnessFixtures.Uses.class);

    assertThat(uses.properties())
        .extracting(Property::type)
        .containsExactly(
            new Named("Box", List.of(new OrNull(TypeRef.STRING))),
            new Named("Box", List.of(TypeRef.STRING)));
    TypeDecl box = reader.declarations().get(0);
    assertThat(((ObjectType) box).properties())
        .containsExactly(
            Property.required("value", new OrNull(new Variable("T"))),
            Property.required("required", new Variable("T")));
  }

  @Test
  void datesAreIsoStringsByDefault() {
    var reader = new TypeReader(JsonMapper.builder().build());

    assertThat(read(reader, NullnessFixtures.Times.class).properties())
        .extracting(Property::type)
        .containsExactly(
            TypeRef.STRING,
            TypeRef.STRING,
            TypeRef.STRING,
            TypeRef.STRING,
            TypeRef.STRING,
            TypeRef.STRING,
            Named.of("DayOfWeek"),
            new OrNull(TypeRef.NUMBER));
    assertThat(reader.declarations()).contains(dayOfWeek());
  }

  @Test
  void datesFollowTheMapperConfiguration() {
    ObjectMapper timestamps =
        JsonMapper.builder()
            .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

    assertThat(read(new TypeReader(timestamps), NullnessFixtures.Times.class).properties())
        .extracting(Property::type)
        .startsWith(
            TypeRef.NUMBER,
            new ArrayOf(TypeRef.NUMBER),
            new ArrayOf(TypeRef.NUMBER),
            TypeRef.NUMBER,
            TypeRef.NUMBER,
            TypeRef.NUMBER);
  }

  private static EnumType dayOfWeek() {
    return new EnumType(
        "DayOfWeek",
        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"));
  }
}
