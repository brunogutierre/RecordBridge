package io.github.brunogutierre.recordbridge.generator.fixture.other;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A controller outside the base package of some tests. */
@RestController
public class OtherController {
  @GetMapping("/other")
  public int other() {
    return 1;
  }
}
