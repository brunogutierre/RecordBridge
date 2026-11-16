package io.github.brunogutierre.recordbridge.reader;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Controllers the API reader is tested against. */
@SuppressWarnings("unused")
final class ControllerFixtures {

  private ControllerFixtures() {}

  enum Status {
    OPEN,
    CLOSED
  }

  record Order(long id, Status status) {}

  record Filter(@Nullable String q, @Nullable Integer minTotal) {}

  @RestController
  @RequestMapping("/api/orders/")
  public static class OrderController {

    @GetMapping
    public List<Order> list(
        @RequestParam(required = false) @Nullable Status status,
        @RequestParam(defaultValue = "0") int page,
        String q,
        Locale locale,
        Principal principal) {
      return List.of();
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
      return new Order(id, Status.OPEN);
    }

    @GetMapping("{orderId}/lines/{line}")
    public Order line(@PathVariable("orderId") long id, @PathVariable int line) {
      return get(id);
    }

    @PostMapping
    public ResponseEntity<Order> create(
        @RequestBody Order order,
        @RequestHeader("X-Request-Id") String requestId,
        UriComponentsBuilder uris) {
      return ResponseEntity.ok(order);
    }

    @PutMapping("/{id}")
    public CompletableFuture<Order> replace(@PathVariable long id, @RequestBody Order order) {
      return CompletableFuture.completedFuture(order);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
      return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/note")
    public void note(@PathVariable long id, @RequestBody(required = false) @Nullable String note) {}

    @GetMapping("/search")
    public List<Order> search(Filter filter) {
      return List.of();
    }

    @GetMapping("/export")
    public String export() {
      return "";
    }

    @GetMapping("/{id}/expanded")
    public @Nullable Order expanded(@PathVariable long id, @RequestParam Optional<String> expand) {
      return null;
    }

    @SuppressWarnings("rawtypes")
    @GetMapping("/raw")
    public ResponseEntity raw() {
      return ResponseEntity.ok().build();
    }

    public String notAnEndpoint() {
      return "";
    }
  }

  @Controller
  @ResponseBody
  public static class LegacyController {
    @RequestMapping(path = "/legacy", method = RequestMethod.GET)
    public Status ping() {
      return Status.OPEN;
    }
  }

  @Controller
  public static class PageController {
    @GetMapping("/")
    public String index() {
      return "index";
    }
  }

  @RestController
  public static class AnyMethodController {
    @RequestMapping("/any")
    public void any() {}
  }

  @RestController
  public static class MapController {
    @GetMapping("/all")
    public void all(@RequestParam Map<String, String> params) {}
  }
}
