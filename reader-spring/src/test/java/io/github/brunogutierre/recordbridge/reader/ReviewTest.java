package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Param.Kind;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Cases found in the review of real-world controller shapes. */
class ReviewTest {

  private final ApiReader reader =
      new ApiReader(JsonMapper.builder().build(), UnmarkedNullness.NON_NULL);

  private ApiModel read(Class<?> type) {
    return reader.read(List.of(type));
  }

  private static Endpoint endpoint(ApiModel model, String name) {
    return model.controllers().get(0).endpoints().stream()
        .filter(e -> e.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void genericBaseControllersUseTheBoundTypes() {
    ApiModel model = read(ReviewFixtures.ItemController.class);

    assertThat(endpoint(model, "get").params())
        .containsExactly(new Param("id", "id", Kind.PATH, TypeRef.NUMBER, true));
    assertThat(endpoint(model, "get").response()).isEqualTo(new Response.Json(Named.of("Item")));
    assertThat(endpoint(model, "list").response())
        .isEqualTo(new Response.Json(new ArrayOf(Named.of("Item"))));
  }

  @Test
  void dtosExtendingAGenericBaseUseTheBoundTypes() {
    ApiModel model = read(ReviewFixtures.StageController.class);

    assertThat(((ObjectType) model.type("ItemPage")).properties())
        .containsExactly(
            Property.required("items", new ArrayOf(Named.of("Item"))),
            Property.required("total", TypeRef.NUMBER));
  }

  @Test
  void parameterAnnotationsDeclaredOnInterfacesCount() {
    Endpoint get = endpoint(read(ReviewFixtures.StageController.class), "get");

    assertThat(get.path()).isEqualTo("/stages/{id}");
    assertThat(get.params())
        .extracting(Param::kind)
        .containsExactly(Kind.PATH, Kind.QUERY, Kind.QUERY);
  }

  @Test
  void enumParametersUseConstantNamesWhenTheyDifferFromJson() {
    ApiModel model = read(ReviewFixtures.StageController.class);
    Endpoint get = endpoint(model, "get");

    assertThat(get.params().get(1).type()).isEqualTo(Named.of("StageParam"));
    assertThat(get.params().get(2).type()).isEqualTo(Named.of("Color"));
    assertThat(model.type("StageParam"))
        .isEqualTo(new EnumType("StageParam", List.of("IN_PROGRESS", "DONE")));
    assertThat(model.type("Stage"))
        .isEqualTo(new EnumType("Stage", List.of("in_progress", "done")));
  }

  @Test
  void overloadsGetDistinctStableNames() {
    ApiModel model = read(ReviewFixtures.SearchController.class);

    assertThat(model.controllers().get(0).endpoints())
        .extracting(Endpoint::name)
        .containsExactly("find", "findByQAndLimit", "paged");
  }

  @Test
  void pageableAndPageFollowSpringData() {
    ApiModel model = read(ReviewFixtures.SearchController.class);
    Endpoint paged = endpoint(model, "paged");

    assertThat(paged.params())
        .extracting(Param::name)
        .containsExactly("page", "size", "sort", "stages");
    assertThat(paged.params().get(3).type()).isEqualTo(new ArrayOf(Named.of("StageParam")));
    assertThat(paged.response())
        .isEqualTo(new Response.Json(new Named("PagedModel", List.of(Named.of("Item")))));
    assertThat(model.types()).extracting(TypeDecl::name).contains("PagedModel", "PageMetadata");
  }

  @Test
  void classLevelProducesMakesAFluxAStream() {
    Endpoint ticks =
        read(ReviewFixtures.TickController.class).controllers().get(0).endpoints().get(0);

    assertThat(ticks.response()).isEqualTo(new Response.EventStream(TypeRef.NUMBER));
  }

  @Test
  void rejectsObjectsAsQueryParameters() {
    assertThatThrownBy(() -> read(ReviewFixtures.PojoQueryController.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("filter) can't be sent as a query parameter");
  }

  @Test
  void rejectsUnknownSpringParameterTypes() {
    assertThatThrownBy(() -> read(ReviewFixtures.UploadController.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining(
            "Unsupported Spring parameter type org.springframework.web.multipart.MultipartFile");
  }
}
