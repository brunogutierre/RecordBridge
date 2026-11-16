package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ErrorReaderTest {

  private final ApiReader reader =
      new ApiReader(JsonMapper.builder().build(), UnmarkedNullness.NON_NULL);

  @Test
  void readsHandlerBodiesFromAdvicesAndControllers() {
    ApiModel model =
        reader.read(
            List.of(
                ErrorFixtures.GlobalErrors.class,
                ErrorFixtures.ItemController.class,
                ErrorFixtures.Unrelated.class));

    assertThat(model.errors())
        .containsExactly(
            Named.of("ProblemDetail"), Named.of("ValidationErrors"), Named.of("Conflict"));
    assertThat(model.types())
        .extracting(TypeDecl::name)
        .contains("ProblemDetail", "ValidationErrors", "FieldError", "Conflict");
  }

  @Test
  void problemDetailIsAlwaysAnError() {
    ApiModel model = reader.read(List.of(ControllerFixtures.LegacyController.class));

    assertThat(model.errors()).containsExactly(Named.of("ProblemDetail"));
    assertThat(model.type("ProblemDetail")).isEqualTo(ErrorReader.PROBLEM_DETAIL_TYPE);
  }
}
