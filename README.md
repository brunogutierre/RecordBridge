# RecordBridge

[![CI](https://github.com/brunogutierre/RecordBridge/actions/workflows/ci.yml/badge.svg)](https://github.com/brunogutierre/RecordBridge/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.brunogutierre.recordbridge/recordbridge-maven-plugin)](https://central.sonatype.com/namespace/io.github.brunogutierre.recordbridge)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Generate TypeScript types and modern Angular services straight from your Spring Boot controllers and Java records. No OpenAPI step, no hand-written DTO mirrors, no drift.

> **0.1.0 is out.** The libraries and the Maven plugin are on Maven Central. The Gradle plugin is waiting for its first approval on the Gradle Plugin Portal. Until then, Gradle builds can't resolve it from the portal.

## Why

A Spring Boot + Angular team usually keeps the API contract in sync in one of two ways:
- by hand, copying every DTO into a TypeScript interface;
- through an OpenAPI pipeline that boots the app, exports a spec and runs a generator, losing sealed types, nullability and generics on the way.

RecordBridge reads the compiled classes at build time, with the same Jackson and Spring rules the app uses at runtime, and writes plain Angular code you can commit. When you change a Java record and rebuild, the Angular compiler points at every place that needs updating.

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface Shape permits Circle, Square {}
public record Circle(double radius) implements Shape {}
public record Square(double side, @Nullable String label) implements Shape {}

@RestController
@RequestMapping("/api/shapes")
class ShapeController {
  @GetMapping("/{id}")
  Shape get(@PathVariable long id) { ... }
}
```

```ts
// models.ts (generated, abridged)
export interface Circle { type: 'Circle'; radius: number; }
export interface Square { type: 'Square'; side: number; label: string | null; }
export type Shape = Circle | Square;

// shape.api.ts (generated, abridged)
@Injectable({ providedIn: 'root' })
export class ShapeApi {
  get(id: number): Observable<Shape> { ... }
  getResource(request: () => { id: number } | undefined): HttpResourceRef<Shape | undefined> { ... }
}
```

## Quick start

### Gradle

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.github.brunogutierre.recordbridge") version "0.1.0"
}

recordBridge {
    basePackages = listOf("com.example.api")        // default: every compiled class
    outputDir = file("../web/src/app/api")           // default: build/generated/recordbridge
    unmarkedNullness = "NON_NULL"                    // or "NULLABLE", for code outside @NullMarked
}
```

```bash
./gradlew generateTypeScript
```

- The task reads the `main` source set's compiled classes, so it runs after `classes`. It is cacheable and supports the configuration cache.
- The classes are loaded in a worker process running the project's Java toolchain (override with the task's `javaLauncher`), so code compiled for a newer Java than the Gradle daemon works.
- Only files that start with the RecordBridge header are replaced: hand-written files in the output folder are kept.
- Parameter names need the `-parameters` compiler flag; the Spring Boot plugin already enables it.

### Maven

```xml
<plugin>
  <groupId>io.github.brunogutierre.recordbridge</groupId>
  <artifactId>recordbridge-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>  <!-- bound to process-classes -->
    </execution>
  </executions>
  <configuration>
    <basePackages><basePackage>com.example.api</basePackage></basePackages>
    <outputDirectory>${project.basedir}/../web/src/app/api</outputDirectory>  <!-- default: target/recordbridge -->
    <unmarkedNullness>NON_NULL</unmarkedNullness>
  </configuration>
</plugin>
```

`mvn process-classes` (or any later phase) generates the files; `-Drecordbridge.skip=true` skips it. Parameter names need `<maven.compiler.parameters>true</maven.compiler.parameters>`, which the Spring Boot parent already sets.

### Angular

The generated services only need Angular's `HttpClient`:

```ts
bootstrapApplication(App, { providers: [provideHttpClient(withFetch())] });
// Another host? { provide: API_BASE_URL, useValue: 'https://api.example.com' } (default: same origin)
```

```ts
import { TaskApi, apiError, type PriorityParam, type TaskEvent } from './api';

export class App {
  private readonly api = inject(TaskApi);
  protected readonly filter = signal<PriorityParam | undefined>(undefined);

  // Signals: reloads whenever filter() changes
  protected readonly tasks = this.api.listResource(() => ({ priority: this.filter() }));

  create(title: string) {
    // RxJS: plain Observables for commands
    this.api.create({ title, priority: 'low' }).subscribe(() => this.tasks.reload());
  }

  open(id: string) {
    this.api.get(id).subscribe({
      // Typed error bodies from the @ExceptionHandlers (ProblemDetail by default)
      error: (error: unknown) => console.warn(apiError(error)?.title),
    });
  }

  follow() {
    // Flux<ServerSentEvent<TaskEvent>> becomes a typed stream
    this.api.progress().subscribe((event: TaskEvent) => console.log(event.type));
  }
}
```

[`examples/spring-angular`](examples/spring-angular) is the full version: a Spring Boot 4 + Angular 22 app that uses the whole generated layer. Its Playwright tests run against the real server in CI, and the generated files are committed so you can read real output.

## What gets generated

| Output | Content |
|---|---|
| `models.ts` | One `interface` per record/POJO, a union per polymorphic type, a `XValues` array and `X` type per enum, and the `ApiError` union |
| `<name>.api.ts` | One `@Injectable({ providedIn: 'root' })` service per `@RestController` |
| `api-config.ts` | `API_BASE_URL` token, `apiError()`, and the helpers the services use (query params, headers, SSE parsing) |
| `index.ts` | Re-exports everything |

### Types

| Java | TypeScript |
|---|---|
| `String`, `char`, `UUID`, `URI`, `Locale`, `Currency`, `byte[]` (Base64) | `string` |
| `int`, `long`, `double`, `BigDecimal`, `BigInteger`, … | `number` |
| `boolean` / `Boolean` | `boolean` |
| `java.time` types, `Date` | `string`, or `number` / `number[]` when the mapper writes timestamps. RecordBridge serializes a sample with your mapper settings to decide |
| `List<T>`, `Set<T>`, arrays, any `Iterable<T>` | `T[]` |
| `Map<String, V>` | `Record<string, V>`; with enum keys, `Partial<Record<E, V>>` |
| enum | `export const XValues = [...] as const; export type X = ...`, using `@JsonProperty`/`@JsonValue` names |
| record, POJO | `interface` with the properties Jackson writes: naming strategy, `@JsonProperty`, `@JsonIgnore(Properties)`, `@JsonUnwrapped`, write-only properties |
| `@JsonTypeInfo` hierarchy, sealed or not | discriminated union, with the property and ids Jackson writes |
| generic records | generic interfaces (`Page<T>`); Spring Data `Page<T>` becomes Spring's `PagedModel` shape |
| `@Nullable` (JSpecify) under `@NullMarked`, `Optional<T>` | `T \| null`; properties Jackson can omit (`@JsonInclude`) are `prop?: T` |
| `JsonNode`, `Object` | `unknown` |

### Endpoints

- `@GetMapping` … `@DeleteMapping` and `@RequestMapping`, including mappings inherited from interfaces and generic base controllers.
- Path variables and the request body are positional arguments. Query parameters (including POJO-bound ones and `Pageable` as `page`/`size`/`sort`) and headers go in a `params` object.
- Enum query parameters use the constant names Spring binds (`PriorityParam`), not the JSON names.
- Every GET gets an `xResource()` factory (`httpResource`). `String` responses are read as text.
- `Flux<T>` / `Flux<ServerSentEvent<T>>` served as `text/event-stream` become `Observable<T>`, parsed from JSON or kept as text.
- `@ExceptionHandler` methods in `@ControllerAdvice`/controllers make up the `ApiError` union; `ProblemDetail` is always included.

### Not supported yet

- File uploads (`MultipartFile`) and binary responses (`byte[]`, `Resource`): the build fails with a clear message instead of generating a wrong signature.
- `SseEmitter`, which doesn't say what its events contain; return `Flux<ServerSentEvent<T>>` instead.
- WebFlux functional routes; only annotated controllers are read.
- Spring Boot 3 / Jackson 2. RecordBridge targets Spring Boot 4 and Jackson 3 only.
- Kotlin: not tested yet, and Kotlin nullability isn't read.

See [ROADMAP.md](ROADMAP.md#4-extras-after-010) for what comes next: Bean Validation → Angular Signal Forms, uploads, Kotlin, Zod and React output.

## Compared with the alternatives

Checked on 2026-11-19 against each project's documentation and release notes (sources below). RecordBridge 0.1.0 is new; the others are mature projects with years of production use.

| | RecordBridge 0.1.0 | springdoc 3.2 + openapi-generator 7.27 `typescript-angular` | typescript-generator 4.2 | Hilla 25.3 |
|---|---|---|---|---|
| Reads | compiled classes, at build time | an OpenAPI spec from the **running** app | compiled classes, at build time | Hilla `@BrowserCallable` services |
| Works with existing `@RestController`s | ✅ | ✅ | ✅ (Spring client module) | ❌ |
| Spring Boot 4 / Jackson 3 | ✅ | ✅ | ✅ (since 4.0) | ✅ |
| `@JsonTypeInfo` / sealed → discriminated union | ✅ | partial: depends on springdoc's schema; `oneOf` is listed as unsupported, `taggedUnions` is opt-in | ✅ | partial (`@JsonTypeInfo` fixes in recent releases; not documented) |
| JSpecify nullability | ✅ including `@NullMarked` defaults | opt-in property for `@Nullable`; no `@NullMarked` | configurable `nullableAnnotations`; no `@NullMarked` | ✅ `@NonNull`/`@Nullable`; no `@NullMarked` documented |
| Angular services | ✅ | ✅ | ❌ framework-neutral client over an adapter you supply | ❌ React (Lit deprecated) |
| `httpResource` (signals) | ✅ | ✅ opt-in `withHttpResource` (Oct 2026) | ❌ | ❌ |
| Typed error bodies | ✅ from `@ExceptionHandler`s | ❌ | ❌ | ❌ |
| Server-sent events | ✅ `Observable<T>` | ❌ | ❌ | ✅ via its own `Subscription` client |
| Runtime library | none | none | an HTTP adapter (e.g. the axios extension) | `@vaadin/hilla-frontend` |

**When to pick something else**
- You need clients for several languages, or a published API contract: **springdoc + openapi-generator**.
- Your frontend isn't Angular, or you're on Spring Boot 3 / Jackson 2: **typescript-generator**.
- You're starting a full-stack React app and want Vaadin's framework and components: **Hilla**.

Sources:
- typescript-generator: [README](https://github.com/vojtechhabarta/typescript-generator), [releases](https://github.com/vojtechhabarta/typescript-generator/releases), [JAX-RS client wiki](https://github.com/vojtechhabarta/typescript-generator/wiki/JAX-RS-Application), [Maven reference](http://www.habarta.cz/typescript-generator/maven/typescript-generator-maven-plugin/generate-mojo.html)
- openapi-generator: [typescript-angular docs](https://openapi-generator.tech/docs/generators/typescript-angular), [releases](https://github.com/OpenAPITools/openapi-generator/releases)
- springdoc: [springdoc.org](https://springdoc.org/)
- Hilla: [docs](https://vaadin.com/docs/latest/hilla), [type nullability](https://vaadin.com/docs/latest/hilla/reference/type-nullability), [reactive services](https://vaadin.com/docs/latest/hilla/guides/reactive-services), [TypeScript client](https://vaadin.com/docs/latest/hilla/reference/typescript-client), [releases](https://github.com/vaadin/hilla/releases)

Coming from typescript-generator? See the [migration guide](docs/migrating-from-typescript-generator.md).

## Architecture

```
compiled classes ──► reader-spring ──► model (records) ──► emitter-ts ──► models.ts + *.api.ts
                      (reflection +                         (Angular
                       Jackson 3 introspection)               services)
        ▲
  generator ◄── gradle-plugin / maven-plugin (thin wrappers)
```

### Design decisions

| Decision | Why | Rejected |
|---|---|---|
| Read compiled classes with reflection, and let **Jackson 3 itself** say which properties, names and type ids it writes | The output matches the wire format, including naming strategies, mix-ins, `@JsonUnwrapped` and polymorphism, without starting the app | An annotation processor (can't reuse Jackson's rules); springdoc's OpenAPI as input (loses sealed types, nullability, generics; needs a running app) |
| Spring's own `MergedAnnotations`, `AnnotatedMethod` and `ResolvableType` for mappings | Same results as Spring for meta-annotations, interface annotations and generic base controllers | Reading annotations by hand |
| A dependency-free intermediate model (`model` module) | Readers and emitters change independently, and the model can be tested in isolation | Emitting TypeScript straight from reflection |
| Plain generated code, no runtime library | Nothing to version or upgrade on the frontend; the code is readable and reviewable | A shared client runtime (hidden behavior) |
| `Observable` methods **and** `httpResource` factories | Fits RxJS code and signal-first code | Signals only (breaks existing RxJS code) |
| Generation runs in a worker on the project's toolchain; Spring and Jackson come from the plugin's class loader | Apps compiled for a newer Java than the Gradle daemon still load; annotations keep one identity | Loading classes in the daemon |
| Golden files + `tsc --strict` + an end-to-end app | Golden files show every output change in review; `tsc` and Playwright prove it compiles and works against real Spring | Asserting on fragments of strings |
| Bytecode for Java 17, built with JDK 25 | Runs in any build on Spring Boot 4's baseline | Requiring the newest JDK in users' builds |

The full plan and the rejected alternatives are in [ROADMAP.md](ROADMAP.md#2-architecture-decisions).

## Stack

- Java 25 toolchain, bytecode for Java 17+; Spring Framework 7 / Spring Boot 4, Jackson 3, JSpecify
- Gradle multi-module build with convention plugins; Gradle plugin and Maven plugin
- Output for Angular 22+ (standalone, signals, `httpResource`); checked with TypeScript 7 `--strict`
- Tests: JUnit 6 + AssertJ, golden files, Gradle TestKit, a real Maven build, Playwright on the example app
- Release: JReleaser to Maven Central, `com.gradle.plugin-publish` to the Plugin Portal

## How to run and test

Requirements: JDK 17 or newer to run Gradle; the build provisions a JDK 25 toolchain (it uses a local JDK 25 if one is installed).

```bash
./gradlew build          # compile, test and coverage report for every module
./gradlew spotlessApply  # format Java (google-java-format) and Gradle scripts (ktlint)
```

Run `spotlessApply` on its own, not in the same invocation as `build`: with the configuration cache, Gradle may run both in parallel.

Emitter tests compare the output with golden files in `emitter-ts/src/test/resources/golden/`. After an intended change to the output:

```bash
./gradlew :emitter-ts:test -PupdateGolden   # rewrite the golden files, then review the diff
npm ci && npm run check:ts                  # strict tsc over the golden files, with Angular and RxJS types (also run in CI)
npm run test:ts                             # runtime tests of generated helpers, such as the SSE parser (Node strips the types)
```

### Project layout

| Path | Purpose |
|---|---|
| `build-logic/` | Convention plugins `recordbridge.java-library` and `recordbridge.published` (POM, sources/javadoc jars, staging repository); `java-library` sets the toolchain, `--release 17`, `-parameters`, `-Werror`, JUnit, JaCoCo, Spotless |
| `gradle/libs.versions.toml` | Version catalog shared by the build and `build-logic` |
| `model/` | Intermediate model (no dependencies besides JSpecify annotations) |
| `reader-spring/` | Compiled Spring classes → model |
| `emitter-ts/` | Model → TypeScript and Angular |
| `generator/` | Scan compiled classes → read → emit → write; shared by the build plugins |
| `gradle-plugin/` | `io.github.brunogutierre.recordbridge` plugin, tested with Gradle TestKit |
| `maven-plugin/` | `recordbridge:generate` goal, tested by running a real Maven build |

The Maven plugin tests publish every module to `build/test-repo` and unpack a Maven distribution first; both happen automatically in `./gradlew build` (network access needed).

## Releasing

A tag `v<major>.<minor>.<patch>` on `main` runs [`release.yml`](.github/workflows/release.yml):

1. Job `maven-central`: `./gradlew build publishAllPublicationsToStagingRepository -Pversion=<tag>` tests, then stages every library module and the Maven plugin (jar, sources, javadoc, POM) in `build/staging-deploy`. The version comes from the tag, so `gradle.properties` keeps a `-SNAPSHOT` version and no release commit is needed. [JReleaser](https://jreleaser.org) (`jreleaser.yml`) then checks the Maven Central rules, signs every file, uploads one bundle through the Central Portal API and waits up to an hour for it to be published.
2. Job `gradle-plugin`, after the first one: `:gradle-plugin:publishPlugins` publishes the Gradle plugin to the Plugin Portal; its dependencies resolve from Maven Central. A new plugin waits for manual approval by the Gradle team.

If a job fails, use **Re-run failed jobs**: the jobs are separate, and `maven-central` skips the deploy when repo1 already has the version (Central rejects a second upload). The Plugin Portal step can also run locally from the tag: `GRADLE_PUBLISH_KEY=… GRADLE_PUBLISH_SECRET=… ./gradlew :gradle-plugin:publishPlugins -Pversion=<version>`.

Why the JReleaser CLI instead of its Gradle plugin: the plugin needs JGit 5.13 and Spotless needs JGit 7, and both would share the build classpath.

Repository secrets used: `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (Central Portal user token), `GPG_SECRET_KEY`, `GPG_PUBLIC_KEY`, `GPG_PASSPHRASE` (armored, key published to keyserver.ubuntu.com), `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`.

Dry run on a local machine, with the JReleaser CLI and the same environment variables the workflow sets:

```bash
./gradlew publishAllPublicationsToStagingRepository -Pversion=0.1.0
jreleaser deploy --dry-run   # verifies POMs, signs and builds the bundle without uploading
```

## License

[Apache-2.0](LICENSE)
