package io.github.brunogutierre.recordbridge.reader;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error handlers the error reader is tested against. */
@SuppressWarnings("unused")
final class ErrorFixtures {

  private ErrorFixtures() {}

  record FieldError(String field, String message) {}

  record ValidationErrors(List<FieldError> errors) {}

  record Conflict(String reason) {}

  @RestControllerAdvice
  public static class GlobalErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidationErrors> invalid() {
      return ResponseEntity.badRequest().body(new ValidationErrors(List.of()));
    }

    @ExceptionHandler
    public ProblemDetail notFound(NoSuchElementException e) {
      return ProblemDetail.forStatus(404);
    }

    @ExceptionHandler(IllegalStateException.class)
    public void ignored() {}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> text() {
      return ResponseEntity.internalServerError().body("oops");
    }
  }

  @RestController
  public static class ItemController {
    @GetMapping("/items")
    public List<String> list() {
      return List.of();
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Conflict> conflict() {
      return ResponseEntity.status(409).body(new Conflict("busy"));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ErrorResponseException rethrow(ErrorResponseException e) {
      return e;
    }
  }

  /** Not a controller and not an advice: its handler is ignored. */
  public static class Unrelated {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<FieldError> ignored() {
      return ResponseEntity.ok(new FieldError("a", "b"));
    }
  }
}
