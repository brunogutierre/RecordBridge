package io.github.brunogutierre.recordbridge.emitter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl.ObjectType;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelsEmitterTest {

  private final ModelsEmitter emitter = new ModelsEmitter();

  @Test
  void matchesTheGoldenFile() throws Exception {
    Golden.assertMatches("models.ts", emitter.emit(SampleModel.model()));
  }

  @Test
  void outputDoesNotDependOnDeclarationOrder() {
    var reversed = new java.util.ArrayList<>(SampleModel.types());
    java.util.Collections.reverse(reversed);

    assertThat(emitter.emit(new ApiModel(reversed, List.of())))
        .isEqualTo(emitter.emit(SampleModel.model()));
  }

  @Test
  void emptyModelIsJustTheHeader() {
    assertThat(emitter.emit(new ApiModel(List.of(), List.of()))).isEqualTo(ModelsEmitter.HEADER);
  }

  @Test
  void escapesStringLiterals() {
    assertThat(Ts.string("a'b\\c\nd\u0001")).isEqualTo("'a\\'b\\\\c\\nd\\u0001'");
  }

  @Test
  void parenthesizesNullableArrayElements() {
    var type =
        ObjectType.of(
            "Box",
            List.of(
                Property.required(
                    "grid",
                    new TypeRef.ArrayOf(new TypeRef.ArrayOf(new TypeRef.OrNull(TypeRef.NUMBER))))));

    assertThat(emitter.emit(new ApiModel(List.of(type), List.of())))
        .contains("  grid: (number | null)[][];\n");
  }
}
