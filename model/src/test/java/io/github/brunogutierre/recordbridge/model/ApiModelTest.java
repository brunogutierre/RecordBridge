package io.github.brunogutierre.recordbridge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Tag;
import io.github.brunogutierre.recordbridge.model.TypeDecl.UnionType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Variant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiModelTest {

  private static final ObjectType PAGE =
      new ObjectType(
          "Page",
          List.of("T"),
          List.of(Property.required("items", new TypeRef.ArrayOf(new TypeRef.Variable("T")))),
          List.of());

  private static final ObjectType ORDER =
      ObjectType.of("Order", List.of(Property.required("id", TypeRef.NUMBER)));

  private static ApiModel withTypes(TypeDecl... types) {
    return new ApiModel(List.of(types), List.of());
  }

  private static ApiModel returning(TypeRef body, TypeDecl... types) {
    var endpoint =
        new Endpoint("list", HttpMethod.GET, "/orders", List.of(), new Response.Json(body));
    return new ApiModel(List.of(types), List.of(new Controller("OrderApi", List.of(endpoint))));
  }

  @Test
  void acceptsAClosedModel() {
    var body = new TypeRef.Named("Page", List.of(TypeRef.Named.of("Order")));

    var model = returning(body, PAGE, ORDER);

    assertThat(model.type("Page")).isEqualTo(PAGE);
  }

  @Test
  void rejectsUnknownTypes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(TypeRef.Named.of("Order")))
        .withMessageContaining("endpoint OrderApi.list refers to unknown type Order");
  }

  @Test
  void rejectsWrongNumberOfTypeArguments() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(TypeRef.Named.of("Page"), PAGE))
        .withMessageContaining("passes 0 type arguments to Page, which takes 1");
  }

  @Test
  void rejectsTypeVariablesOutsideTheirDeclaration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(new TypeRef.Variable("T")))
        .withMessageContaining("outside its declaration");
    var leaky = ObjectType.of("Leaky", List.of(Property.required("x", new TypeRef.Variable("T"))));
    assertThatIllegalArgumentException().isThrownBy(() -> withTypes(leaky));
  }

  @Test
  void checksReferencesInsideContainers() {
    var nested =
        new TypeRef.OrNull(
            new TypeRef.MapOf(TypeRef.STRING, new TypeRef.ArrayOf(TypeRef.Named.of("Missing"))));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(nested))
        .withMessageContaining("Missing");
  }

  @Test
  void mapKeysAreStringsOrEnums() {
    var status = new EnumType("Status", List.of("OPEN"));
    var byStatus = new TypeRef.MapOf(TypeRef.Named.of("Status"), TypeRef.NUMBER);
    var byOrder = new TypeRef.MapOf(TypeRef.Named.of("Order"), TypeRef.NUMBER);

    assertThat(returning(byStatus, status).types()).containsExactly(status);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(byOrder, ORDER))
        .withMessageContaining("map key");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> returning(new TypeRef.MapOf(TypeRef.NUMBER, TypeRef.NUMBER)));
  }

  @Test
  void checksParameterTypes() {
    var param = new Param("filter", "filter", Param.Kind.QUERY, TypeRef.Named.of("Filter"), false);
    var endpoint = new Endpoint("list", HttpMethod.GET, "/orders", List.of(param), Response.EMPTY);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ApiModel(List.of(), List.of(new Controller("OrderApi", List.of(endpoint)))))
        .withMessageContaining("Filter");
  }

  @Test
  void checksEventStreamTypes() {
    var endpoint =
        new Endpoint(
            "events",
            HttpMethod.GET,
            "/events",
            List.of(),
            new Response.EventStream(TypeRef.Named.of("Event")));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ApiModel(List.of(), List.of(new Controller("EventApi", List.of(endpoint)))));
  }

  @Test
  void unionVariantsMustBeTaggedObjects() {
    var circle =
        new ObjectType(
            "Circle",
            List.of(),
            List.of(Property.required("radius", TypeRef.NUMBER)),
            List.of(new Tag("type", "circle")));
    var shape =
        new UnionType(
            "Shape", List.of(), "type", List.of(new Variant("circle", TypeRef.Named.of("Circle"))));
    var wrongTag =
        new UnionType(
            "Shape", List.of(), "type", List.of(new Variant("round", TypeRef.Named.of("Circle"))));
    var status = new EnumType("Status", List.of("OPEN"));
    var enumVariant =
        new UnionType(
            "Shape", List.of(), null, List.of(new Variant(null, TypeRef.Named.of("Status"))));

    assertThat(withTypes(circle, shape).types()).hasSize(2);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> withTypes(circle, wrongTag))
        .withMessageContaining("lacks the tag");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> withTypes(status, enumVariant))
        .withMessageContaining("is not an object type");
  }

  @Test
  void rejectsDuplicateNames() {
    assertThatIllegalArgumentException().isThrownBy(() -> withTypes(ORDER, ORDER));
    var api = new Controller("OrderApi", List.of());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiModel(List.of(), List.of(api, api)));
  }

  @Test
  void checksErrorTypes() {
    var model = new ApiModel(List.of(ORDER), List.of(), List.of(TypeRef.Named.of("Order")));

    assertThat(model.errors()).containsExactly(TypeRef.Named.of("Order"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiModel(List.of(), List.of(), List.of(TypeRef.Named.of("Problem"))))
        .withMessageContaining("error type refers to unknown type Problem");
    assertThat(withTypes().errors()).isEmpty();
  }

  @Test
  void unknownTypeLookupFails() {
    assertThatIllegalArgumentException().isThrownBy(() -> withTypes().type("Order"));
  }
}
