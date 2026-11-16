package io.github.brunogutierre.recordbridge.emitter;

import static io.github.brunogutierre.recordbridge.model.TypeRef.NUMBER;
import static io.github.brunogutierre.recordbridge.model.TypeRef.STRING;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Param.Kind;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;

/** Controllers over {@link SampleModel} that use every feature of the service emitter. */
final class SampleApi {

  private SampleApi() {}

  private static final Named ORDER = SampleModel.ORDER;
  private static final Named STATUS = Named.of("Status");

  static ApiModel model() {
    var orders =
        new Controller(
            "OrderApi",
            List.of(
                new Endpoint(
                    "list",
                    HttpMethod.GET,
                    "/api/orders",
                    List.of(
                        new Param("status", "status", Kind.QUERY, STATUS, false),
                        new Param("tags", "tag", Kind.QUERY, new ArrayOf(STRING), false)),
                    new Response.Json(new Named("Page", List.of(ORDER)))),
                new Endpoint(
                    "get",
                    HttpMethod.GET,
                    "/api/orders/{id:\\d+}",
                    List.of(new Param("id", "id", Kind.PATH, NUMBER, true)),
                    new Response.Json(ORDER)),
                new Endpoint(
                    "create",
                    HttpMethod.POST,
                    "/api/orders",
                    List.of(
                        new Param("order", "order", Kind.BODY, ORDER, true),
                        new Param("requestId", "X-Request-Id", Kind.HEADER, STRING, true)),
                    new Response.Json(ORDER)),
                new Endpoint(
                    "note",
                    HttpMethod.PATCH,
                    "/api/orders/{orderId}/note",
                    List.of(
                        new Param("id", "orderId", Kind.PATH, NUMBER, true),
                        new Param("note", "note", Kind.BODY, STRING, false),
                        new Param("in", "in", Kind.QUERY, STRING, true)),
                    Response.EMPTY),
                new Endpoint(
                    "export",
                    HttpMethod.GET,
                    "/api/orders/export/{params}",
                    List.of(
                        new Param("params", "params", Kind.PATH, STRING, true),
                        new Param("format", "format", Kind.QUERY, STRING, false)),
                    Response.TEXT),
                new Endpoint(
                    "delete",
                    HttpMethod.DELETE,
                    "/api/orders/{id}",
                    List.of(new Param("id", "id", Kind.PATH, NUMBER, true)),
                    Response.EMPTY)));
    var files =
        new Controller(
            "FileStoreApi",
            List.of(
                new Endpoint(
                    "read",
                    HttpMethod.GET,
                    "/files/{*path}",
                    List.of(new Param("path", "path", Kind.PATH, STRING, true)),
                    Response.TEXT)));
    return new ApiModel(SampleModel.types(), List.of(orders, files));
  }
}
