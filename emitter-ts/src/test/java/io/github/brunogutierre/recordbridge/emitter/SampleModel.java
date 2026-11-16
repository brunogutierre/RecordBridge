package io.github.brunogutierre.recordbridge.emitter;

import static io.github.brunogutierre.recordbridge.model.TypeRef.BOOLEAN;
import static io.github.brunogutierre.recordbridge.model.TypeRef.NUMBER;
import static io.github.brunogutierre.recordbridge.model.TypeRef.STRING;
import static io.github.brunogutierre.recordbridge.model.TypeRef.UNKNOWN;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl.EnumType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Tag;
import io.github.brunogutierre.recordbridge.model.TypeDecl.UnionType;
import io.github.brunogutierre.recordbridge.model.TypeDecl.Variant;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import io.github.brunogutierre.recordbridge.model.TypeRef.ArrayOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.MapOf;
import io.github.brunogutierre.recordbridge.model.TypeRef.Named;
import io.github.brunogutierre.recordbridge.model.TypeRef.OrNull;
import io.github.brunogutierre.recordbridge.model.TypeRef.Variable;
import java.util.List;

/** A model that uses every feature of the type emitter. */
final class SampleModel {

  private SampleModel() {}

  static final Named ORDER = Named.of("Order");

  static List<io.github.brunogutierre.recordbridge.model.TypeDecl> types() {
    var page =
        new ObjectType(
            "Page",
            List.of("T"),
            List.of(
                Property.required("items", new ArrayOf(new Variable("T"))),
                Property.required("total", NUMBER)),
            List.of());
    var status = new EnumType("Status", List.of("OPEN", "CLOSED", "it's \"done\""));
    var order =
        ObjectType.of(
            "Order",
            List.of(
                Property.required("id", NUMBER),
                Property.required("status", Named.of("Status")),
                Property.required("note", new OrNull(STRING)),
                new Property("coupon", STRING, true),
                Property.required("tags", new ArrayOf(new OrNull(STRING))),
                Property.required("totals", new MapOf(STRING, NUMBER)),
                Property.required("countByStatus", new MapOf(Named.of("Status"), NUMBER)),
                Property.required("shape", Named.of("Shape")),
                Property.required("extra", UNKNOWN),
                Property.required("paid", BOOLEAN),
                Property.required("first-name", STRING)));
    var circle =
        new ObjectType(
            "Circle",
            List.of(),
            List.of(Property.required("radius", NUMBER)),
            List.of(new Tag("@type", "circle")));
    var square =
        new ObjectType(
            "Square",
            List.of(),
            List.of(Property.required("side", NUMBER)),
            List.of(new Tag("@type", "square")));
    var shape =
        new UnionType(
            "Shape",
            List.of(),
            "@type",
            List.of(
                new Variant("circle", Named.of("Circle")),
                new Variant("square", Named.of("Square"))));
    var ok =
        new ObjectType(
            "Ok",
            List.of("T"),
            List.of(Property.required("value", new Variable("T"))),
            List.of(new Tag("kind", "ok")));
    var err =
        new ObjectType(
            "Err",
            List.of(),
            List.of(Property.required("message", STRING)),
            List.of(new Tag("kind", "err")));
    var result =
        new UnionType(
            "Result",
            List.of("T"),
            "kind",
            List.of(
                new Variant("ok", new Named("Ok", List.of(new Variable("T")))),
                new Variant("err", Named.of("Err"))));
    var orderPage =
        ObjectType.of(
            "OrderSearch",
            List.of(
                Property.required("page", new Named("Page", List.of(ORDER))),
                Property.required(
                    "lookup", new Named("Result", List.of(new OrNull(TypeRef.NUMBER))))));
    return List.of(page, status, order, circle, square, shape, ok, err, result, orderPage);
  }

  static ApiModel model() {
    return new ApiModel(types(), List.of());
  }
}
