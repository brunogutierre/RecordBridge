# RecordBridge example: Spring Boot 4 + Angular 22

A small task API and an Angular page that uses every part of the generated API layer. It is also RecordBridge's end-to-end test: CI regenerates the API (and fails if the committed files are out of date), builds the Angular app and runs Playwright against the real Spring Boot server.

| What | Where |
|---|---|
| Spring Boot API | [`src/main/java/example`](src/main/java/example) |
| Generated TypeScript (committed, never edited) | [`web/src/app/api`](web/src/app/api) |
| Angular page | [`web/src/app/app.ts`](web/src/app/app.ts) |
| End-to-end tests | [`web/e2e/app.spec.ts`](web/e2e/app.spec.ts) |

The build applies the RecordBridge Gradle plugin from this repository (`pluginManagement { includeBuild("../..") }`).

## What the end-to-end tests prove on the wire

- An enum query parameter is sent by constant name (`HIGH`), as Spring binds it, even though its JSON value is `high`.
- An enum request body is sent as JSON (`"high"`, `application/json`), not as `text/plain`.
- A `ProblemDetail` error is read through `apiError()`.
- A `Flux<ServerSentEvent<TaskEvent>>` stream arrives as a typed union.
- A `Flux<String>` stream arrives as plain text.
- `listResource()` reloads when the filter signal changes.

## Run it

```bash
# from the repository root
./gradlew -p examples/spring-angular generateTypeScript   # regenerate web/src/app/api
cd examples/spring-angular/web
npm ci
npx ng build
npx playwright install chromium
npx playwright test          # starts the app with bootRun on http://localhost:8187
```
