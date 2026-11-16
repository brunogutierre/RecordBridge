package io.github.brunogutierre.recordbridge.emitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AngularEmitterTest {

  private final Map<String, String> files = new AngularEmitter().emit(SampleApi.model());

  @Test
  void writesTheWholeApiLayer() {
    assertThat(files)
        .containsOnlyKeys(
            "models.ts",
            "api-config.ts",
            "chat.api.ts",
            "file-store.api.ts",
            "order.api.ts",
            "index.ts");
  }

  @Test
  void matchesTheGoldenFiles() throws Exception {
    for (var file : files.entrySet()) {
      Golden.assertMatches("angular/" + file.getKey(), file.getValue());
    }
  }

  @Test
  void importsOnlyTheHelpersAndModelsItUses() {
    assertThat(files.get("file-store.api.ts"))
        .contains("import { API_BASE_URL } from './api-config';")
        .doesNotContain("from './models'");
  }

  @Test
  void namesFilesAfterServices() {
    assertThat(ServiceEmitter.fileName(new Controller("OrderLineApi", List.of())))
        .isEqualTo("order-line.api.ts");
    assertThat(ServiceEmitter.fileName(new Controller("Legacy", List.of())))
        .isEqualTo("legacy.api.ts");
  }

  @Test
  void resourceFactoriesOnlyForGetsWithoutBody() {
    assertThat(files.get("order.api.ts"))
        .contains("  getResource(", "  listResource(", "  exportResource(")
        .doesNotContain("createResource", "deleteResource", "noteResource");
  }

  @Test
  void rejectsAResourceNameThatClashesWithAnEndpoint() {
    var get =
        new Endpoint("get", HttpMethod.GET, "/a", List.of(), new Response.Json(TypeRef.STRING));
    var getResource = new Endpoint("getResource", HttpMethod.POST, "/b", List.of(), Response.EMPTY);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ServiceEmitter().emit(new Controller("AApi", List.of(get, getResource))))
        .withMessageContaining("AApi.getResource clashes");
  }

  @Test
  void typedErrorsOnlyWhenTheModelHasThem() {
    var bare = new AngularEmitter().emit(new ApiModel(List.of(), List.of()));

    assertThat(bare.get("api-config.ts")).doesNotContain("apiError", "HttpErrorResponse");
    assertThat(bare.get("models.ts")).doesNotContain("ApiError");
    assertThat(files.get("api-config.ts")).contains("import type { ApiError } from './models';");
  }

  @Test
  void escapesReservedParameterNames() {
    assertThat(ServiceEmitter.safe("in")).isEqualTo("in_");
    assertThat(ServiceEmitter.safe("id")).isEqualTo("id");
  }
}
