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
    files.put("api-config.ts", CONFIG);
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

  private static final String CONFIG =
      ModelsEmitter.HEADER
          + """

          import { HttpHeaders, HttpParams } from '@angular/common/http';
          import { InjectionToken } from '@angular/core';

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
}
