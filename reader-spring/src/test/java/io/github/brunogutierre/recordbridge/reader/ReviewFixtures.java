package io.github.brunogutierre.recordbridge.reader;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

/** Real-world controller shapes: generic bases, interfaces, overloads, Spring Data. */
@SuppressWarnings("unused")
final class ReviewFixtures {

  private ReviewFixtures() {}

  enum Stage {
    @JsonProperty("in_progress")
    IN_PROGRESS,
    @JsonProperty("done")
    DONE
  }

  enum Color {
    RED,
    GREEN
  }

  record Item(long id, Stage stage) {}

  public abstract static class CrudController<T, ID> {
    @GetMapping("/{id}")
    public T get(@PathVariable ID id) {
      throw new UnsupportedOperationException();
    }

    @GetMapping
    public List<T> list() {
      return List.of();
    }
  }

  @RestController
  @RequestMapping("/items")
  public static class ItemController extends CrudController<Item, Long> {}

  public static class PageResponse<T> {
    public List<T> getItems() {
      return List.of();
    }

    public int getTotal() {
      return 0;
    }
  }

  public static class ItemPage extends PageResponse<Item> {}

  public interface StageApi {
    @GetMapping("/stages/{id}")
    Item get(@PathVariable("id") long id, @RequestParam Stage stage, @RequestParam Color color);
  }

  @RestController
  public static class StageController implements StageApi {
    @Override
    public Item get(long id, Stage stage, Color color) {
      return new Item(id, stage);
    }

    @GetMapping("/pages")
    public ItemPage page() {
      return new ItemPage();
    }
  }

  @RestController
  public static class SearchController {
    @GetMapping("/search")
    public List<Item> find() {
      return List.of();
    }

    @GetMapping("/search/by-text")
    public List<Item> find(@RequestParam String q, @RequestParam int limit) {
      return List.of();
    }

    @GetMapping("/search/paged")
    public Page<Item> paged(Pageable pageable, @RequestParam List<Stage> stages) {
      throw new UnsupportedOperationException();
    }
  }

  @RestController
  @RequestMapping(path = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public static class TickController {
    @GetMapping
    public Flux<Long> ticks() {
      return Flux.empty();
    }
  }

  public static class Filter {
    public String getQ() {
      return "";
    }
  }

  @RestController
  public static class PojoQueryController {
    @GetMapping("/filter")
    public List<Item> filter(Filter filter) {
      return List.of();
    }
  }

  @RestController
  public static class UploadController {
    @PostMapping("/upload")
    public void upload(MultipartFile file) {}
  }
}
