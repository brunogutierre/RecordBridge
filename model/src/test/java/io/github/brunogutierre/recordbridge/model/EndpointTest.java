package io.github.brunogutierre.recordbridge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.brunogutierre.recordbridge.model.Param.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointTest {

  private static Param path(String name) {
    return new Param(name, name, Kind.PATH, TypeRef.NUMBER, true);
  }

  @Test
  void readsSpringPathVariables() {
    assertThat(Endpoint.pathVariables("/orders/{id}/lines/{line:\\d+}/{*rest}"))
        .containsExactly("id", "line", "rest");
  }

  @Test
  void pathVariablesMustMatchPathParameters() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new Endpoint("get", HttpMethod.GET, "/orders/{id}", List.of(), Response.EMPTY))
        .withMessageContaining("[id]");
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new Endpoint("list", HttpMethod.GET, "/orders", List.of(path("id")), Response.EMPTY));
  }

  @Test
  void pathParameterUsesTheWireName() {
    var param = new Param("orderId", "id", Kind.PATH, TypeRef.NUMBER, true);

    var endpoint =
        new Endpoint("get", HttpMethod.GET, "/orders/{id}", List.of(param), Response.EMPTY);

    assertThat(endpoint.params()).containsExactly(param);
  }

  @Test
  void pathParametersAreRequired() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Param("id", "id", Kind.PATH, TypeRef.NUMBER, false));
  }

  @Test
  void atMostOneBody() {
    var a = new Param("a", "a", Kind.BODY, TypeRef.STRING, true);
    var b = new Param("b", "b", Kind.BODY, TypeRef.STRING, true);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new Endpoint("save", HttpMethod.POST, "/orders", List.of(a, b), Response.EMPTY))
        .withMessageContaining("more than one body");
  }

  @Test
  void pathMustBeAbsolute() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new Endpoint("list", HttpMethod.GET, "orders", List.of(), Response.EMPTY));
  }

  @Test
  void controllerRejectsDuplicateEndpoints() {
    var list = new Endpoint("list", HttpMethod.GET, "/orders", List.of(), Response.EMPTY);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Controller("OrderApi", List.of(list, list)));
  }
}
