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
```

### Project layout

| Path | Purpose |
|---|---|
| `build-logic/` | Convention plugin `recordbridge.java-library`: toolchain, `--release 17`, `-parameters`, `-Werror`, JUnit, JaCoCo, Spotless |
| `gradle/libs.versions.toml` | Version catalog shared by the build and `build-logic` |
| `model/` | Intermediate model (no dependencies besides JSpecify annotations) |
| `reader-spring/` | Compiled Spring classes → model |
| `emitter-ts/` | Model → TypeScript and Angular |

Gradle and Maven plugin modules are added in milestone M3.

## License

[Apache-2.0](LICENSE)
