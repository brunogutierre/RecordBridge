package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StreamTest {

  private final ApiReader reader =
      new ApiReader(JsonMapper.builder().build(), UnmarkedNullness.NON_NULL);

  private Endpoint endpoint(String name) {
    return reader
        .read(List.of(StreamFixtures.ChatController.class))
        .controllers()
        .get(0)
        .endpoints()
        .stream()
        .filter(e -> e.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void serverSentEventsAreTypedStreams() {
    var model = reader.read(List.of(StreamFixtures.ChatController.class));

    assertThat(endpoint("ask").response()).isEqualTo(new Response.EventStream(Named.of("Event")));
    assertThat(model.types())
        .extracting(TypeDecl::name)
        .contains("Event", "Token", "Done", "Question");
  }

  @Test
  void eventStreamMediaTypeMakesAFluxAStream() {
    assertThat(endpoint("ticks").response()).isEqualTo(new Response.EventStream(TypeRef.NUMBER));
  }

  @Test
  void stringStreamsAreText() {
    assertThat(endpoint("tokens").response())
        .isEqualTo(new Response.EventStream(TypeRef.STRING, true));
    assertThat(endpoint("ask").response())
        .isEqualTo(new Response.EventStream(Named.of("Event"), false));
  }

  @Test
  void otherFluxesAreJsonArrays() {
    assertThat(endpoint("numbers").response())
        .isEqualTo(new Response.Json(new TypeRef.ArrayOf(TypeRef.NUMBER)));
  }

  @Test
  void rejectsBinaryResponses() {
    assertThatThrownBy(() -> reader.read(List.of(BinaryController.class)))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("returns binary content");
  }

  @org.springframework.web.bind.annotation.RestController
  public static class BinaryController {
    @org.springframework.web.bind.annotation.GetMapping("/file")
    public byte[] file() {
      return new byte[0];
    }
  }

  @Test
  void rejectsUntypedSseEmitters() {
    assertThatThrownBy(() -> reader.read(List.of(StreamFixtures.EmitterController.class)))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("return Flux<ServerSentEvent<T>> instead");
  }
}
