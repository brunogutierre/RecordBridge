# RecordBridge roadmap

## 1. Description

RecordBridge is a Gradle/Maven build plugin. It reads the compiled classes of a Spring Boot application and generates:

- **TypeScript types** for every record, POJO, enum and sealed hierarchy that crosses the HTTP boundary, with the JSON shape Jackson actually produces;
- **Angular services** for every `@RestController`: `HttpClient` methods returning `Observable`s, `httpResource` factories for GET endpoints, typed errors and typed server-sent-event streams.

Generated code is plain, readable and meant to be committed. There is no runtime library.

### Target users

- Teams running Spring Boot + Angular who hand-write DTO interfaces or maintain an OpenAPI pipeline only to get types.
- Java developers who want the frontend build to break when the API changes.

### Non-goals (v1)

- Spring Boot 3 / Jackson 2 (Spring Boot 4 + Jackson 3 only)
- WebFlux functional routes (annotated controllers only)
- React, Vue or Zod output (listed as extras)
- OpenAPI output

## 2. Architecture decisions

| Decision | Choice | Why | Rejected |
|---|---|---|---|
| How to read the API | Build plugin that loads compiled classes and uses reflection; **Jackson 3 introspection** (`BeanDescription`) decides property names and polymorphism; Spring `MergedAnnotations` and `ResolvableType` resolve mappings and generics | Matches exactly what goes over the wire (`@JsonProperty`, naming strategies, `@JsonTypeInfo`), needs no application context, also works on Kotlin and Lombok bytecode | **Annotation processor**: verbose mirror API, runs inside javac, can't reuse Jackson rules, Kotlin needs KSP. **springdoc OpenAPI as the intermediate model**: loses sealed types, nullability and generics, needs the app to boot, and it would just be another openapi-generator |
| Intermediate model | Plain Java records in a dependency-free `model` module (`TypeRef`, `ObjectType`, `UnionType`, `EnumType`, `Endpoint`, `Param`) | Readers and emitters evolve independently; easy to snapshot | Emitting TypeScript directly from reflection |
| Output | One `models.ts` plus one `*.api.ts` per controller | Easy to review in diffs, tree-shakable | One file per type (noisy), a runtime client library (hidden behavior) |
| Angular API | `@Injectable` service with `Observable` methods, plus `httpResource` factories for GETs, base URL via an `InjectionToken` | Fits both RxJS code and signal-first code | Signals only (breaks existing RxJS code) |
| Errors | RFC 9457 `ProblemDetail` mapped to a typed `ApiError` union | Spring Boot 4 uses ProblemDetail by default | Untyped `HttpErrorResponse` |
| Nullability | JSpecify (`@Nullable`, `@NullMarked`), configurable | It's the standard Spring 7 adopted | Guessing from `Optional` only |
| Java baseline | JDK 25 toolchain, `--release 17` | Spring Boot 4 baseline is 17; Gradle/Maven daemons often run 17/21 | Requiring 25 at build time |
| Requirement | `-parameters` compiler flag (Spring Boot plugins already enable it); clear error if missing | Parameter names are needed for query/path params | Parsing source files |
| Publishing | Maven Central (Central Portal via JReleaser) and Gradle Plugin Portal | Where Java users look for plugins | JitPack (looks unprofessional) |
| License | Apache-2.0 | Standard for the Java ecosystem, patent grant | MIT |

### Modules

| Module | Role |
|---|---|
| `model` | Intermediate model records, no dependencies |
| `reader-spring` | Compiled classes → model (Spring + Jackson 3 + JSpecify) |
| `emitter-ts` | Model → TypeScript types and Angular services |
| `gradle-plugin` | `recordBridge { ... }` extension and `generateTypeScript` task |
| `maven-plugin` | `recordbridge:generate` goal |
| `examples/spring-angular` | Spring Boot 4 + Angular 22 app used as an end-to-end test |

### Configuration (draft)

```kotlin
recordBridge {
  basePackages = listOf("com.example.api")
  outputDir = file("../web/src/app/api")
  mode = Mode.ANGULAR            // TYPES | ANGULAR
  dates = DateMode.STRING        // STRING | DATE
  nullability = Nullability.JSPECIFY
  optional = OptionalMode.UNDEFINED
  baseUrlToken = "API_BASE_URL"
}
```

### Testing strategy

- Reader unit tests against fixture classes (one fixture per Java feature).
- Golden-file snapshot tests for the emitter; refresh with `-PupdateGolden`.
- CI runs `tsc --noEmit --strict` on every golden file, so generated code always compiles.
- Gradle TestKit for the Gradle plugin; Maven Invoker for the Maven plugin.
- The example app goes through generate → `ng build` → a Playwright smoke test against the real backend in CI.

## 3. Step by step

Each step is one branch and one PR (≤ ~500 changed lines per commit).

### M0: Foundation
1. `chore: scaffold`: multi-module Gradle, toolchain, Spotless, CI, README skeleton
2. `feat(model)`: intermediate model records, plus tests for their invariants

### M1: Types
3. `feat(reader)`: records, POJOs, enums, primitives, collections and maps via Jackson introspection
4. `feat(reader)`: generics, `Optional`, `java.time`, JSpecify nullability
5. `feat(reader)`: sealed interfaces and `@JsonTypeInfo` → discriminated unions
6. `feat(emitter)`: TypeScript type emitter, golden tests, `tsc` check in CI

### M2: Endpoints and Angular
7. `feat(reader)`: `@RestController` model (mappings; path, query, body and header params; `ResponseEntity`; void)
8. `feat(emitter)`: Angular services (`HttpClient`) plus base-URL token
9. `feat(emitter)`: `httpResource` factories for GET endpoints
10. `feat(emitter)`: typed errors (`ProblemDetail`, `@ExceptionHandler` mapping)
11. `feat(emitter)`: SSE endpoints (`SseEmitter`, `Flux<ServerSentEvent<T>>`) → typed streams. Needed by [AtlasMTL](https://github.com/brunogutierre/AtlasMTL)

### M3: Build integration
12. `feat(gradle)`: Gradle plugin plus TestKit tests
13. `feat(maven)`: Maven plugin plus Invoker tests
14. `test(example)`: Spring + Angular example app with e2e in CI

### M4: Release
15. `chore(release)`: Central Portal and Gradle Plugin Portal publishing, 0.1.0
16. `docs`: full README (decisions, verified comparison table, migration guide from typescript-generator)

**Phase 0 check (before PR 1):** confirm "RecordBridge" is free on Maven Central, the Gradle Plugin Portal and npm, and verify the `io.github.brunogutierre` namespace on the Central Portal.

## 4. Extras (after 0.1.0)

- **Bean Validation → Angular Signal Forms** validators and schema. This is the strongest differentiator: the backend constraints show up in the form.
- Spring Data `Page<T>` / `Slice<T>` helpers
- Multipart uploads and file downloads
- Kotlin data classes and Kotlin nullability
- Zod schemas for runtime validation
- React output (TanStack Query hooks)
- Watch mode (`--continuous`) for the dev loop
- JBang one-liner CLI

## 5. Visibility plan

- 60-second GIF in the README: change a record and the Angular compile error appears.
- Blog post: *Why I skipped OpenAPI to share types between Spring Boot and Angular*.
- Posts on r/java, r/Angular2 and dev.to; offer a lightning talk to the Montreal Java User Group.
- Dogfooding: [AtlasMTL](https://github.com/brunogutierre/AtlasMTL) generates its whole Angular API layer with RecordBridge, including the SSE agent stream.
