package io.github.brunogutierre.recordbridge.model;

/** What an endpoint sends back on success. */
public sealed interface Response permits Response.Json, Response.Empty, Response.EventStream {

  /** No body, for {@code void} methods and {@code ResponseEntity<Void>}. */
  Empty EMPTY = new Empty();

  /** A JSON body of the given type. */
  record Json(TypeRef body) implements Response {}

  /** No body. */
  record Empty() implements Response {}

  /** A stream of server-sent events whose data is JSON of the given type. */
  record EventStream(TypeRef event) implements Response {}
}
