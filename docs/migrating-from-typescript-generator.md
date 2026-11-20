# Migrating from typescript-generator

This guide moves a Spring Boot + Angular project from [typescript-generator](https://github.com/vojtechhabarta/typescript-generator) (types, optionally with its Spring client) to RecordBridge (types and Angular services).

Before you start, check that:
- the backend runs on **Spring Boot 4 / Jackson 3**, because RecordBridge doesn't read Jackson 2;
- the frontend uses **Angular's `HttpClient`** and a version with `httpResource`. RecordBridge is tested with Angular 22.

## What changes

| | typescript-generator | RecordBridge |
|---|---|---|
| What you list | classes (`classes`, `classPatterns`) | packages (`basePackages`). The types the controllers use are generated wherever they live |
| Output | one `.ts`/`.d.ts` file | a folder: `models.ts`, one `*.api.ts` per controller, `api-config.ts`, `index.ts` |
| REST client | one framework-neutral class that delegates to an HTTP adapter you supply; `Promise` by default | one `@Injectable` Angular service per controller over `HttpClient`, returning `Observable`s plus `httpResource` factories for GETs |
| Enums | TypeScript union or `enum` (`mapEnum`) | a string union plus a `XValues` array (`as const`) |
| Dates | `Date` by default (`mapDate`) | what your mapper really writes: `string` by default in Spring Boot |
| Nullability | annotations you configure (`nullableAnnotations`, `optionalAnnotations`) | JSpecify (`@NullMarked`, `@Nullable`) and `@JsonInclude` |

## 1. Replace the build plugin

### Maven

Before:

```xml
<plugin>
  <groupId>cz.habarta.typescript-generator</groupId>
  <artifactId>typescript-generator-maven-plugin</artifactId>
  <version>4.2.1</version>
  <executions>
    <execution>
      <id>generate</id>
      <goals><goal>generate</goal></goals>
      <phase>process-classes</phase>
    </execution>
  </executions>
  <configuration>
    <jsonLibrary>jackson3</jsonLibrary>
    <classPatterns><pattern>com.example.api.**</pattern></classPatterns>
    <outputFile>../web/src/app/api/api.ts</outputFile>
    <outputKind>module</outputKind>
    <mapDate>asString</mapDate>
  </configuration>
</plugin>
```

After:

```xml
<plugin>
  <groupId>io.github.brunogutierre.recordbridge</groupId>
  <artifactId>recordbridge-maven-plugin</artifactId>
  <version>0.1.1</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>  <!-- process-classes by default -->
    </execution>
  </executions>
  <configuration>
    <basePackages><basePackage>com.example.api</basePackage></basePackages>
    <outputDirectory>${project.basedir}/../web/src/app/api</outputDirectory>
  </configuration>
</plugin>
```

### Gradle

Replace the `cz.habarta.typescript-generator` plugin and its `generateTypeScript { ... }` block:

```kotlin
plugins {
    id("io.github.brunogutierre.recordbridge") version "0.1.1"
}

recordBridge {
    basePackages = listOf("com.example.api")
    outputDir = file("../web/src/app/api")
}
```

The task is also called `generateTypeScript`, so existing `dependsOn` references keep working.

### Option mapping

| typescript-generator | RecordBridge |
|---|---|
| `classes`, `classPatterns` | `basePackages` |
| `outputFile`, `outputKind` | `outputDir` / `outputDirectory`: a folder of ES modules |
| `jsonLibrary` | not needed: always Jackson 3, read through your mapper's annotations |
| `mapDate` | not needed: RecordBridge serializes a sample date to see what the mapper writes |
| `mapEnum`, `nonConstEnums` | not needed: always a union plus a `XValues` array |
| `mapMap` | not needed: always `Record<string, V>` (`Partial<Record<E, V>>` for enum keys) |
| `nullableAnnotations`, `nullabilityDefinition` | JSpecify, plus `unmarkedNullness` (`NON_NULL` or `NULLABLE`) for code outside `@NullMarked` |
| `optionalAnnotations`, `requiredAnnotations`, `optionalProperties` | derived from `@JsonInclude`: properties Jackson may omit become `prop?: T` |
| `generateSpringApplicationClient`, `restResponseType`, `restOptionsType` | always on: Angular services |
| `customTypeMappings`, `customTypeProcessor`, extensions | no equivalent yet. Unsupported JDK types fail the build and name the property |

## 2. Delete the adapter

typescript-generator's client delegates every call to an HTTP adapter: one you wrote, or the axios extension. RecordBridge services use Angular's `HttpClient` directly, so delete the adapter and register `HttpClient` once:

```ts
bootstrapApplication(App, {
  providers: [
    provideHttpClient(withFetch()),
    // only if the API is on another host:
    { provide: API_BASE_URL, useValue: 'https://api.example.com' },
  ],
});
```

Interceptors (auth, retries) keep working, because every call goes through `HttpClient`.

## 3. Update the call sites

Before, one framework-neutral client returning Promises:

```ts
const client = new RestApplicationClient(myHttpAdapter);
const person = await client.getPerson(1);
```

After, one service per controller, with the Java method names:

```ts
private readonly people = inject(PersonApi);   // PersonController → PersonApi

// signal-based, reloads when id() changes
protected readonly person = this.people.getPersonResource(() => ({ id: this.id() }));

save(person: Person) {
  this.people.update(person.id, person).subscribe();   // Observable
}

// still want a Promise? await firstValueFrom(this.people.getPerson(1))
```

Naming rules:
- The service name drops a trailing `Controller` and appends `Api`: `PersonController` → `PersonApi`, `Orders` → `OrdersApi`.
- Every GET without a body that returns JSON or text also gets `<method>Resource`.
- Overloaded Java methods get distinct names. The overload without parameters keeps the name; the others append their parameter names, for example `list`, `listByQ` and `findByQAndLimit`.

## 4. Update enums

```ts
// before (mapEnum = asEnum)
if (order.status === Status.OPEN) { ... }
Object.values(Status).forEach(...)

// after
if (order.status === 'OPEN') { ... }
StatusValues.forEach(...)
```

The values are the JSON names (`@JsonProperty`/`@JsonValue`). Enum **query parameters** use the constant names Spring binds. When they differ from the JSON names, RecordBridge generates a separate `XParam` type with `XParamValues`.

## 5. Check the types the compiler now flags

These differences surface as TypeScript errors. They are real mismatches with what the server sends:

- **Dates:** `string`, not `Date`, unless your mapper writes timestamps. Parse where you need a `Date`.
- **Nullability:** under `@NullMarked`, only `@Nullable` members are `T | null`. Code outside `@NullMarked` follows `unmarkedNullness`.
- **Optional properties:** `prop?: T` only when `@JsonInclude` lets Jackson omit the property. Otherwise it is always present, possibly as `null`.
- **Polymorphism:** `@JsonTypeInfo` hierarchies become discriminated unions with the exact property and ids Jackson writes, so `switch (shape.type)` narrows the type.
- **Errors:** error bodies are typed. Replace `(error as any).error.message` with `apiError(error)?.detail`.

## 6. Clean up

- Delete the old generated file and any hand-written types that mirrored the backend.
- Commit the generated folder and make CI regenerate it and fail on a diff:

  ```bash
  ./gradlew generateTypeScript && git diff --exit-code -- web/src/app/api
  ```

  With Maven, run `mvn process-classes` instead.
- RecordBridge only replaces files that start with its header, so hand-written files in the same folder are safe.
