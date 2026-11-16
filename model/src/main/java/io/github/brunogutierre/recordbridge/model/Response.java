package io.github.brunogutierre.recordbridge.model;

/** What an endpoint sends back on success. */
public sealed interface Response
    permits Response.Json, Response.Text, Response.Empty, Response.EventStream {

  /** No body, for {@code void} methods and {@code ResponseEntity<Void>}. */
  Empty EMPTY = new Empty();

  /** A JSON body of the given type. */
  record Json(TypeRef body) implements Response {}

  /** A plain-text body, as Spring writes a {@code String} return value. */
  Text TEXT = new Text();

  /** A plain-text body. */
  record Text() implements Response {}

  /** No body. */
  record Empty() implements Response {}

  /**
   * A stream of server-sent events.
   *
   * @param event the type of each event's data
   * @param text whether the data is plain text (Spring writes {@code String} data as is) rather
   *     than JSON
   */
  record EventStream(TypeRef event, boolean text) implements Response {
    public EventStream {
      if (text && !event.equals(TypeRef.STRING)) {
        throw new IllegalArgumentException("a text event stream carries strings");
      }
    }

    /** A stream of JSON events. */
    public EventStream(TypeRef event) {
      this(event, false);
    }
  }
}
