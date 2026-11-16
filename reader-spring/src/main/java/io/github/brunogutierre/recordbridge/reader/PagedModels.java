package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.Property;
import io.github.brunogutierre.recordbridge.model.TypeDecl;
import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spring Data pages. Spring Data writes a {@code Page<T>} through its stable {@code PagedModel} DTO
 * ({@code content} plus {@code page} metadata), which is also what this models; matched by class
 * name, so Spring Data doesn't have to be on RecordBridge's classpath.
 */
final class PagedModels {

  private static final Set<String> PAGES =
      Set.of("org.springframework.data.domain.Page", "org.springframework.data.web.PagedModel");

  private static final TypeDecl PAGE_METADATA =
      TypeDecl.ObjectType.of(
          "PageMetadata",
          List.of(
              Property.required("size", TypeRef.NUMBER),
              Property.required("number", TypeRef.NUMBER),
              Property.required("totalElements", TypeRef.NUMBER),
              Property.required("totalPages", TypeRef.NUMBER)));

  private static final TypeDecl PAGED_MODEL =
      new TypeDecl.ObjectType(
          "PagedModel",
          List.of("T"),
          List.of(
              Property.required("content", new TypeRef.ArrayOf(new TypeRef.Variable("T"))),
              Property.required("page", TypeRef.Named.of("PageMetadata"))),
          List.of());

  private boolean used;

  static boolean isPage(Class<?> type) {
    return PAGES.contains(type.getName());
  }

  TypeRef of(TypeRef element) {
    used = true;
    return new TypeRef.Named("PagedModel", List.of(element));
  }

  List<TypeDecl> declarations() {
    var declarations = new ArrayList<TypeDecl>();
    if (used) {
      declarations.add(PAGED_MODEL);
      declarations.add(PAGE_METADATA);
    }
    return declarations;
  }
}
