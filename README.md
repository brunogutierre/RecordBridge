# RecordBridge

Generate TypeScript types and modern Angular services straight from your Spring Boot controllers and Java records. No OpenAPI step, no hand-written DTO mirrors, no drift.

> **Status: planning.** Nothing is published yet. The full plan, design decisions and milestones are in [ROADMAP.md](ROADMAP.md).

## Goal

Keep a Spring Boot backend and an Angular frontend in sync at compile time. You change a Java record, run the build, and the Angular compiler shows you every place that needs updating.

```java
public sealed interface Shape permits Circle, Square {}
public record Circle(double radius) implements Shape {}
public record Square(double side) implements Shape {}

@RestController
@RequestMapping("/api/shapes")
class ShapeController {
  @GetMapping("/{id}")
  Shape get(@PathVariable long id) { ... }
}
```

```ts
// generated
export type Shape = Circle | Square;
export interface Circle { type: 'Circle'; radius: number }
export interface Square { type: 'Square'; side: number }

@Injectable({ providedIn: 'root' })
export class ShapeApi {
  get(id: number): Observable<Shape> { ... }
  getResource(id: () => number): HttpResourceRef<Shape | undefined> { ... }
}
```

## What makes it different

| Capability | RecordBridge (planned) | OpenAPI + openapi-generator | typescript-generator | Hilla |
|---|---|---|---|---|
| Reads compiled Java directly | ✅ | ❌ (needs a spec) | ✅ | ✅ |
| Sealed interfaces → discriminated unions | ✅ | partial (`oneOf`) | ❌ | partial |
| JSpecify nullability | ✅ | ❌ | ❌ | partial |
| Angular services with signals (`httpResource`) | ✅ | ❌ | ❌ | ❌ (Lit/React) |
| Typed errors (RFC 9457 `ProblemDetail`) | ✅ | ❌ | ❌ | ❌ |
| Server-sent events as typed streams | ✅ | ❌ | ❌ | ✅ (own runtime) |
| Plain generated code, no runtime library | ✅ | ✅ | ✅ | ❌ |

The other columns are a first assessment. Each one gets checked against a sample project before the 0.1.0 release.

## Stack (planned)

- Java 25 toolchain, bytecode for Java 17+; Spring Boot 4.x and Jackson 3
- Gradle multi-module build; Gradle plugin and Maven plugin
- Output for Angular 22+ (standalone, signals, `httpResource`)
- Tests: JUnit 6 + AssertJ, golden-file snapshots, `tsc --strict` on the output, Gradle TestKit, Maven Invoker, Playwright on an example app

## Architecture (planned)

```
compiled classes ──► reader-spring ──► model (records) ──► emitter-ts ──► models.ts + *.api.ts
                      (reflection +                         (types or
                       Jackson 3 introspection)               Angular services)
        ▲
  gradle-plugin / maven-plugin (thin wrappers)
```

Main decisions, with their reasons, are in [ROADMAP.md](ROADMAP.md#architecture-decisions).

## Example

[`examples/spring-angular`](examples/spring-angular) is a Spring Boot 4 + Angular 22 app that uses the whole generated layer (resources, typed errors, typed and text event streams). Its Playwright tests run against the real server in CI, and the generated files are committed so you can read real output.

## Usage (Gradle)

> Not published yet: the first release is milestone M4. Until then, use it from a local build (`./gradlew publishToMavenLocal` arrives with the release PR).

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

## Usage (Maven)

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
