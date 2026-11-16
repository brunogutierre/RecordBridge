package io.github.brunogutierre.recordbridge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeRefTest {

  @Test
  void nullableWrapsOnce() {
    TypeRef once = TypeRef.nullable(TypeRef.STRING);

    assertThat(once).isEqualTo(new TypeRef.OrNull(TypeRef.STRING));
    assertThat(TypeRef.nullable(once)).isSameAs(once);
  }

  @Test
  void orNullRejectsNesting() {
    var inner = new TypeRef.OrNull(TypeRef.NUMBER);

    assertThatIllegalArgumentException().isThrownBy(() -> new TypeRef.OrNull(inner));
  }

  @Test
  void namedCopiesItsArguments() {
    var arguments = new ArrayList<TypeRef>(List.of(TypeRef.STRING));
    var named = new TypeRef.Named("Page", arguments);

    arguments.add(TypeRef.NUMBER);

    assertThat(named.arguments()).containsExactly(TypeRef.STRING);
  }

  @Test
  void namesMustBeIdentifiers() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TypeRef.Named.of("Order-Line"))
        .withMessageContaining("Order-Line");
    assertThatIllegalArgumentException().isThrownBy(() -> new TypeRef.Variable("1T"));
  }
}
