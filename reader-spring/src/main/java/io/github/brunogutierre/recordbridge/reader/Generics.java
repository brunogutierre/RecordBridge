package io.github.brunogutierre.recordbridge.reader;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.ResolvableType;

/** Resolves the type variables of a generic supertype as a subclass binds them. */
final class Generics {

  private Generics() {}

  /**
   * What each type variable of {@code declaring} is in {@code owner}: for {@code class
   * OrderController extends CrudController<Order>}, {@code T} of {@code CrudController} is {@code
   * Order}. Variables the subclass leaves open are not in the map.
   */
  static Map<TypeVariable<?>, Type> bindings(Class<?> owner, Class<?> declaring) {
    TypeVariable<?>[] variables = declaring.getTypeParameters();
    if (owner == declaring || variables.length == 0) {
      return Map.of();
    }
    ResolvableType resolved = ResolvableType.forClass(owner).as(declaring);
    var bindings = new HashMap<TypeVariable<?>, Type>();
    for (int i = 0; i < variables.length; i++) {
      Type type = resolved.getGeneric(i).getType();
      if (!type.equals(variables[i]) && resolved.getGeneric(i) != ResolvableType.NONE) {
        bindings.put(variables[i], type);
      }
    }
    return bindings;
  }
}
