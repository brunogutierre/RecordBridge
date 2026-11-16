package io.github.brunogutierre.recordbridge.reader;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/** Streaming endpoints the API reader is tested against. */
@SuppressWarnings("unused")
final class StreamFixtures {

  private StreamFixtures() {}

  sealed interface Event permits Token, Done {}

  record Token(String text) implements Event {}

  record Done(int tokens) implements Event {}

  record Question(String text) {}

  @RestController
  public static class ChatController {
    @PostMapping("/ask")
    public Flux<ServerSentEvent<Event>> ask(@RequestBody Question question) {
      return Flux.empty();
    }

    @GetMapping(path = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Long> ticks() {
      return Flux.empty();
    }

    @GetMapping(path = "/tokens", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> tokens() {
      return Flux.empty();
    }

    @GetMapping("/numbers")
    public Flux<Integer> numbers() {
      return Flux.empty();
    }
  }

  @RestController
  public static class EmitterController {
    @GetMapping("/legacy")
    public SseEmitter legacy() {
      return new SseEmitter();
    }
  }
}
