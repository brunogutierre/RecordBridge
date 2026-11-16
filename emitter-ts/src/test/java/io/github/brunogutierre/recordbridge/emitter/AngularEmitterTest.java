package io.github.brunogutierre.recordbridge.emitter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.recordbridge.model.Controller;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AngularEmitterTest {

  private final Map<String, String> files = new AngularEmitter().emit(SampleApi.model());

  @Test
  void writesTheWholeApiLayer() {
    assertThat(files)
        .containsOnlyKeys(
            "models.ts", "api-config.ts", "file-store.api.ts", "order.api.ts", "index.ts");
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
  void escapesReservedParameterNames() {
    assertThat(ServiceEmitter.safe("in")).isEqualTo("in_");
    assertThat(ServiceEmitter.safe("id")).isEqualTo("id");
  }
}
