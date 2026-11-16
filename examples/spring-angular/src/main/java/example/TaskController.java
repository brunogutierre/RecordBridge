package example;

import example.Tasks.Finished;
import example.Tasks.NewTask;
import example.Tasks.Priority;
import example.Tasks.Progressed;
import example.Tasks.Started;
import example.Tasks.Task;
import example.Tasks.TaskEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** An in-memory task API. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

  public TaskController() {
    add(new NewTask("Read the RecordBridge README", Priority.HIGH));
    add(new NewTask("Water the plants", Priority.LOW));
  }

  @GetMapping
  public List<Task> list(@RequestParam(required = false) @Nullable Priority priority) {
    return tasks.values().stream()
        .filter(t -> priority == null || t.priority() == priority)
        .sorted(Comparator.comparing(Task::title))
        .toList();
  }

  @GetMapping("/{id}")
  public Task get(@PathVariable UUID id) {
    Task task = tasks.get(id);
    if (task == null) {
      throw new NoSuchElementException("No task " + id);
    }
    return task;
  }

  @PostMapping
  public Task create(@RequestBody NewTask task) {
    return add(task);
  }

  @PutMapping("/{id}/priority")
  public Task setPriority(@PathVariable UUID id, @RequestBody Priority priority) {
    Task task = get(id);
    Task updated = new Task(task.id(), task.title(), priority, task.done(), task.note());
    tasks.put(id, updated);
    return updated;
  }

  @GetMapping(path = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<TaskEvent>> progress() {
    var events = new ArrayList<TaskEvent>();
    events.add(new Started(3));
    for (int i = 1; i <= 3; i++) {
      events.add(new Progressed(i));
    }
    events.add(new Finished("3 of 3 done"));
    return Flux.fromIterable(events)
        .delayElements(Duration.ofMillis(50))
        .map(event -> ServerSentEvent.<TaskEvent>builder(event).build());
  }

  @GetMapping(path = "/greeting", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> greeting() {
    return Flux.just("Hello", "from", "Spring").delayElements(Duration.ofMillis(50));
  }

  @GetMapping("/export")
  public String export() {
    var csv = new StringBuilder("title,priority\n");
    list(null).forEach(t -> csv.append(t.title()).append(',').append(t.priority()).append('\n'));
    return csv.toString();
  }

  private Task add(NewTask task) {
    var created = new Task(UUID.randomUUID(), task.title(), task.priority(), false, null);
    tasks.put(created.id(), created);
    return created;
  }
}
