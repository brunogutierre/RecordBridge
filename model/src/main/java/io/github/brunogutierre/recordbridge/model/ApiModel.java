package io.github.brunogutierre.recordbridge.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything RecordBridge knows about an API: its type declarations and controllers.
 *
 * <p>The constructor checks that the model is closed: every {@link TypeRef.Named} points to a
 * declaration with the same number of type arguments, type variables are only used inside the
 * generic declaration that defines them, and union variants are tagged object types.
 */
public record ApiModel(List<TypeDecl> types, List<Controller> controllers) {

  public ApiModel {
    types = List.copyOf(types);
    controllers = List.copyOf(controllers);
    Names.requireUnique(types, TypeDecl::name, "type");
    Names.requireUnique(controllers, Controller::name, "controller");
    new Checker(types).check(controllers);
  }

  /** Finds a declaration by name. */
  public TypeDecl type(String name) {
    return types.stream()
        .filter(t -> t.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown type: " + name));
  }

  private static final class Checker {
    private final Map<String, TypeDecl> byName = new HashMap<>();

    Checker(List<TypeDecl> types) {
      types.forEach(t -> byName.put(t.name(), t));
    }

    void check(List<Controller> controllers) {
      for (TypeDecl decl : byName.values()) {
        Set<String> scope = Set.copyOf(decl.typeParameters());
        String where = "type " + decl.name();
        if (decl instanceof TypeDecl.ObjectType object) {
          object.properties().forEach(p -> ref(p.type(), scope, where));
        } else if (decl instanceof TypeDecl.UnionType union) {
          union.variants().forEach(v -> variant(union, v, scope));
        }
      }
      for (Controller controller : controllers) {
        for (Endpoint endpoint : controller.endpoints()) {
          String where = "endpoint " + controller.name() + "." + endpoint.name();
          endpoint.params().forEach(p -> ref(p.type(), Set.of(), where));
          Response response = endpoint.response();
          if (response instanceof Response.Json json) {
            ref(json.body(), Set.of(), where);
          } else if (response instanceof Response.EventStream stream) {
            ref(stream.event(), Set.of(), where);
          }
        }
      }
    }

    private void variant(TypeDecl.UnionType union, TypeDecl.Variant variant, Set<String> scope) {
      String where = "union " + union.name();
      ref(variant.type(), scope, where);
      TypeDecl target = byName.get(variant.type().name());
      if (!(target instanceof TypeDecl.ObjectType object)) {
        throw new IllegalArgumentException(
            where + " variant " + variant.type().name() + " is not an object type");
      }
      if (union.discriminator() != null) {
        var expected = new TypeDecl.Tag(union.discriminator(), variant.tag());
        if (!object.tags().contains(expected)) {
          throw new IllegalArgumentException(
              where + " variant " + object.name() + " lacks the tag " + expected);
        }
      }
    }

    private void ref(TypeRef ref, Set<String> scope, String where) {
      if (ref instanceof TypeRef.ArrayOf array) {
        ref(array.element(), scope, where);
      } else if (ref instanceof TypeRef.MapOf map) {
        boolean enumKey =
            map.key() instanceof TypeRef.Named key
                && byName.get(key.name()) instanceof TypeDecl.EnumType;
        if (!map.key().equals(TypeRef.STRING) && !enumKey) {
          throw new IllegalArgumentException(where + " has a map key that is not a string or enum");
        }
        ref(map.key(), scope, where);
        ref(map.value(), scope, where);
      } else if (ref instanceof TypeRef.OrNull orNull) {
        ref(orNull.type(), scope, where);
      } else if (ref instanceof TypeRef.Variable variable) {
        if (!scope.contains(variable.name())) {
          throw new IllegalArgumentException(
              where + " uses type variable " + variable.name() + " outside its declaration");
        }
      } else if (ref instanceof TypeRef.Named named) {
        TypeDecl target = byName.get(named.name());
        if (target == null) {
          throw new IllegalArgumentException(where + " refers to unknown type " + named.name());
        }
        if (target.typeParameters().size() != named.arguments().size()) {
          throw new IllegalArgumentException(
              where
                  + " passes "
                  + named.arguments().size()
                  + " type arguments to "
                  + named.name()
                  + ", which takes "
                  + target.typeParameters().size());
        }
        named.arguments().forEach(a -> ref(a, scope, where));
      }
    }
  }
}
