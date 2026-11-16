package io.github.brunogutierre.recordbridge.emitter;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the whole Angular API layer: {@code models.ts}, {@code api-config.ts}, one {@code
 * *.api.ts} service per controller and an {@code index.ts} that re-exports everything.
 */
public final class AngularEmitter {

  private final ModelsEmitter models = new ModelsEmitter();
  private final ServiceEmitter services = new ServiceEmitter();

  /** The generated files, by file name, in a stable order. */
  public Map<String, String> emit(ApiModel model) {
    var files = new LinkedHashMap<String, String>();
    files.put("models.ts", models.emit(model));
    files.put("api-config.ts", config(!model.errors().isEmpty()));
    var index = new StringBuilder(ModelsEmitter.HEADER).append('\n');
    index.append("export * from './api-config';\n");
    index.append("export * from './models';\n");
    model.controllers().stream()
        .sorted(Comparator.comparing(Controller::name))
        .forEach(controller -> {
          String file = ServiceEmitter.fileName(controller);
          files.put(file, services.emit(controller));
          index.append("export * from './").append(file.replace(".ts", "")).append("';\n");
        });
    files.put("index.ts", index.toString());
    return files;
  }

  private static String config(boolean errors) {
    var out = new StringBuilder(ModelsEmitter.HEADER).append('\n');
    String http =
        errors
            ? "HttpErrorResponse, HttpEventType, HttpHeaders, HttpParams, type HttpEvent"
            : "HttpEventType, HttpHeaders, HttpParams, type HttpEvent";
    out.append("import { ").append(http).append(" } from '@angular/common/http';\n");
    out.append("import { InjectionToken } from '@angular/core';\n");
    out.append("import { Observable } from 'rxjs';\n");
    if (errors) {
      out.append("import type { ApiError } from './models';\n");
    }
    out.append(HELPERS).append(SSE);
    if (errors) {
      out.append(API_ERROR);
    }
    return out.toString();
  }

  private static final String SSE =
      """

      /**
       * Turns a `text/event-stream` response, requested with `observe: 'events'` and
       * `reportProgress: true`, into the JSON data of its events, emitted as they arrive.
       * Going through HttpClient keeps interceptors (authentication) and allows POST, which
       * `EventSource` doesn't. Unsubscribing cancels the request.
       */
      export function sseEvents<T>(events: Observable<HttpEvent<string>>): Observable<T> {
        return new Observable<T>((subscriber) => {
          let seen = 0;
          let buffer = '';
          const read = (text: string, final: boolean) => {
            buffer += text.slice(seen);
            seen = text.length;
            const blocks = buffer.split(/\\r?\\n\\r?\\n/);
            buffer = final ? '' : (blocks.pop() ?? '');
            for (const block of blocks) {
              const data = block
                .split(/\\r?\\n/)
                .filter((line) => line.startsWith('data:'))
                .map((line) => line.slice(line.startsWith('data: ') ? 6 : 5))
                .join('\\n');
              if (data !== '') {
                subscriber.next(JSON.parse(data) as T);
              }
            }
          };
          return events.subscribe({
            next: (event) => {
              try {
                if (event.type === HttpEventType.DownloadProgress && event.partialText !== undefined) {
                  read(event.partialText, false);
                } else if (event.type === HttpEventType.Response) {
                  read(event.body ?? '', true);
                  subscriber.complete();
                }
              } catch (error: unknown) {
                subscriber.error(error);
              }
            },
            error: (error: unknown) => subscriber.error(error),
          });
        });
      }
      """;

  private static final String HELPERS =
      """

      /**
       * The URL the API is served from, without a trailing slash. Defaults to the page origin;
       * provide it to call another host: `{ provide: API_BASE_URL, useValue: 'https://api.example.com' }`.
       */
      export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
        factory: () => '',
      });

      type Scalar = string | number | boolean;

      /** Query parameters without the undefined and null values; arrays repeat the key. */
      export function httpParams(
        values: Record<string, Scalar | readonly Scalar[] | null | undefined>,
      ): HttpParams {
        let params = new HttpParams();
        for (const [key, value] of Object.entries(values)) {
          if (value !== null && value !== undefined) {
            params = params.appendAll({ [key]: value });
          }
        }
        return params;
      }

      /** Request headers without the undefined and null values. */
      export function httpHeaders(values: Record<string, Scalar | null | undefined>): HttpHeaders {
        let headers = new HttpHeaders();
        for (const [key, value] of Object.entries(values)) {
          if (value !== null && value !== undefined) {
            headers = headers.set(key, String(value));
          }
        }
        return headers;
      }
      """;

  private static final String API_ERROR =
      """

      /**
       * The typed body of a failed request, from the error of an `Observable` or of a resource;
       * `undefined` when there is none (network failure, empty or non-JSON body). The type comes
       * from the API's exception handlers; the body itself is not validated.
       */
      export function apiError(error: unknown): ApiError | undefined {
        if (error instanceof HttpErrorResponse && typeof error.error === 'object' && error.error !== null) {
          return error.error as ApiError;
        }
        return undefined;
      }
      """;
}
