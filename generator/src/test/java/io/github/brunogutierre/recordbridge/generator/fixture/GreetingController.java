package io.github.brunogutierre.recordbridge.generator.fixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A tiny application controller the generator is tested against. */
@RestController
public class GreetingController {

  /** A greeting. */
  public record Greeting(String message) {}

  @GetMapping("/greetings/{name}")
  public Greeting greet(@PathVariable String name) {
    Runnable anonymous =
        new Runnable() {
          @Override
          public void run() {}
        };
    anonymous.run();
    return new Greeting("Hello, " + name);
  }
}
