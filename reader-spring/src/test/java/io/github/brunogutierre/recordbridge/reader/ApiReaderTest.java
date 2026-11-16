package io.github.brunogutierre.recordbridge.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import io.github.brunogutierre.recordbridge.model.HttpMethod;
import io.github.brunogutierre.recordbridge.model.Param;
import io.github.brunogutierre.recordbridge.model.Param.Kind;
import io.github.brunogutierre.recordbridge.model.Response;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiReaderTest {

  private static final Named ORDER = Named.of("Order");

  private final ApiReader reader =
      new ApiReader(JsonMapper.builder().build(), UnmarkedNullness.NON_NULL);

  private ApiModel read(Class<?>... classes) {
    return reader.read(List.of(classes));
  }

  private Endpoint endpoint(String name) {
    return read(ControllerFixtures.OrderController.class).controllers().get(0).endpoints().stream()
        .filter(e -> e.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static Param query(String name, TypeRef type, boolean required) {
    return new Param(name, name, Kind.QUERY, type, required);
  }

  @Test
  void readsOneServicePerRestController() {
    ApiModel model =
        read(
            ControllerFixtures.OrderController.class,
            ControllerFixtures.LegacyController.class,
            ControllerFixtures.PageController.class,
            String.class);

    assertThat(model.controllers())
        .extracting(Controller::name)
        .containsExactly("LegacyApi", "OrderApi");
    assertThat(model.types()).extracting(TypeDecl::name).contains("Order", "Status");
    assertThat(model.controllers().get(1).endpoints())
        .extracting(Endpoint::name)
        .containsExactly(
            "create",
            "delete",
            "expanded",
            "export",
            "get",
            "line",
            "list",
            "note",
            "raw",
            "replace",
            "search");
  }

  @Test
  void readsQueryParameters() {
    assertThat(endpoint("list"))
        .isEqualTo(
            new Endpoint(
                "list",
                HttpMethod.GET,
                "/api/orders",
                List.of(
                    query("status", Named.of("Status"), false),
                    query("page", TypeRef.NUMBER, false),
                    query("q", TypeRef.STRING, false)),
                new Response.Json(new ArrayOf(ORDER))));
  }

  @Test
  void readsPathVariablesWithTheirWireNames() {
    assertThat(endpoint("line").path()).isEqualTo("/api/orders/{orderId}/lines/{line}");
    assertThat(endpoint("line").params())
        .containsExactly(
            new Param("id", "orderId", Kind.PATH, TypeRef.NUMBER, true),
            new Param("line", "line", Kind.PATH, TypeRef.NUMBER, true));
  }

  @Test
  void readsBodiesAndHeadersAndSkipsServerSideParameters() {
    assertThat(endpoint("create"))
        .isEqualTo(
            new Endpoint(
                "create",
                HttpMethod.POST,
                "/api/orders",
                List.of(
                    new Param("order", "order", Kind.BODY, ORDER, true),
                    new Param("requestId", "X-Request-Id", Kind.HEADER, TypeRef.STRING, true)),
                new Response.Json(ORDER)));
    assertThat(endpoint("note").params())
        .contains(new Param("note", "note", Kind.BODY, TypeRef.STRING, false));
  }

  @Test
  void unwrapsResponseEntitiesAndAsyncResults() {
    assertThat(endpoint("replace").method()).isEqualTo(HttpMethod.PUT);
    assertThat(endpoint("replace").response()).isEqualTo(new Response.Json(ORDER));
    assertThat(endpoint("delete").response()).isEqualTo(Response.EMPTY);
    assertThat(endpoint("note").response()).isEqualTo(Response.EMPTY);
    assertThat(endpoint("note").method()).isEqualTo(HttpMethod.PATCH);
    assertThat(endpoint("raw").response()).isEqualTo(new Response.Json(TypeRef.UNKNOWN));
  }

  @Test
  void stringResultsArePlainText() {
    assertThat(endpoint("export").response()).isEqualTo(Response.TEXT);
  }

  @Test
  void nullableResultsAreStillJson() {
    assertThat(endpoint("expanded").response()).isEqualTo(new Response.Json(ORDER));
    assertThat(endpoint("expanded").params()).contains(query("expand", TypeRef.STRING, false));
  }

  @Test
  void bindsRecordsAsQueryParameters() {
    assertThat(endpoint("search").params())
        .containsExactly(
            query("q", TypeRef.STRING, false), query("minTotal", TypeRef.NUMBER, false));
  }

  @Test
  void readsControllerAndResponseBodyStereotypes() {
    Endpoint ping =
        read(ControllerFixtures.LegacyController.class).controllers().get(0).endpoints().get(0);

    assertThat(ping)
        .isEqualTo(
            new Endpoint(
                "ping",
                HttpMethod.GET,
                "/legacy",
                List.of(),
                new Response.Json(Named.of("Status"))));
  }

  @Test
  void requiresExactlyOneHttpMethod() {
    assertThatThrownBy(() -> read(ControllerFixtures.AnyMethodController.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("AnyMethodController.any must be mapped to exactly one HTTP method");
  }

  @Test
  void rejectsMapsOfAllParameters() {
    assertThatThrownBy(() -> read(ControllerFixtures.MapController.class))
        .isInstanceOf(UnsupportedTypeException.class)
        .hasMessageContaining("declare each parameter");
  }

  @Test
  void joinsPaths() {
    assertThat(EndpointReader.join("", "")).isEqualTo("/");
    assertThat(EndpointReader.join("/api/", "/orders/")).isEqualTo("/api/orders");
    assertThat(EndpointReader.join("api", "{id}")).isEqualTo("/api/{id}");
  }

  @Test
  void namesServicesAfterControllers() {
    assertThat(ApiReader.serviceName(ControllerFixtures.OrderController.class))
        .isEqualTo("OrderApi");
    assertThat(ApiReader.serviceName(ControllerFixtures.Order.class)).isEqualTo("OrderApi");
  }
}
