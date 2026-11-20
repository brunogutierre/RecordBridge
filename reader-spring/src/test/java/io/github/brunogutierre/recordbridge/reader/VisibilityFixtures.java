package io.github.brunogutierre.recordbridge.reader;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/** Handlers that aren't public, which Spring maps anyway. */
final class VisibilityFixtures {

  private VisibilityFixtures() {}

  record Note(String text) {}

  record Failure(String reason) {}

  @RestController
  @RequestMapping("/notes")
  static class NoteController {
    @GetMapping("/{id}")
    Note get(@PathVariable long id) {
      return new Note("note " + id);
    }

    @GetMapping
    private Note latest() {
      return new Note("latest");
    }

    @GetMapping("/count")
    protected int count() {
      return 0;
    }

    Note notAHandler() {
      return new Note("helper");
    }
  }

  static class BaseController {
    @GetMapping("/ping")
    String ping() {
      return "base";
    }
  }

  /** Overrides an inherited handler: Spring maps it once. */
  @RestController
  static class PingController extends BaseController {
    @Override
    String ping() {
      return "override";
    }
  }

  @ControllerAdvice
  @ResponseBody
  static class Errors {
    @ExceptionHandler(IllegalStateException.class)
    Failure conflict(IllegalStateException e) {
      return new Failure(e.getMessage());
    }
  }
}
