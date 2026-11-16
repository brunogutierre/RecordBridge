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

  /** A stream of server-sent events whose data is JSON of the given type. */
  record EventStream(TypeRef event) implements Response {}
}
