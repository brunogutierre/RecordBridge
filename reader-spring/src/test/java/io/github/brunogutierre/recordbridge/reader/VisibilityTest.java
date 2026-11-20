package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class VisibilityTest {

  private final ApiReader reader =
      new ApiReader(JsonMapper.builder().build(), UnmarkedNullness.NON_NULL);

  private ApiModel read(Class<?>... classes) {
    return reader.read(List.of(classes));
  }

  @Test
  void readsHandlersOfAnyVisibilityLikeSpring() {
    List<Endpoint> endpoints =
        read(VisibilityFixtures.NoteController.class).controllers().get(0).endpoints();

    assertThat(endpoints).extracting(Endpoint::name).containsExactly("count", "get", "latest");
    assertThat(endpoints)
        .extracting(Endpoint::response)
        .containsExactly(
            new Response.Json(TypeRef.NUMBER),
            new Response.Json(Named.of("Note")),
            new Response.Json(Named.of("Note")));
  }

  @Test
  void readsAnOverriddenInheritedHandlerOnce() {
    List<Endpoint> endpoints =
        read(VisibilityFixtures.PingController.class).controllers().get(0).endpoints();

    assertThat(endpoints).extracting(Endpoint::path).containsExactly("/ping");
  }

  @Test
  void readsExceptionHandlersOfAnyVisibility() {
    ApiModel model = read(VisibilityFixtures.Errors.class, VisibilityFixtures.NoteController.class);

    assertThat(model.errors()).contains(Named.of("Failure"));
  }
}
