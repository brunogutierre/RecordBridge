package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The types of the task API. */
final class Tasks {

  private Tasks() {}

  /** JSON values differ from the constant names on purpose: Spring binds parameters by name. */
  enum Priority {
    @JsonProperty("low")
    LOW,
    @JsonProperty("high")
    HIGH
  }

  record Task(UUID id, String title, Priority priority, boolean done, @Nullable String note) {}

  record NewTask(String title, Priority priority) {}

  /** What the event stream sends, told apart by its "type" property. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  sealed interface TaskEvent permits Started, Progressed, Finished {}

  @JsonTypeName("started")
  record Started(int total) implements TaskEvent {}

  @JsonTypeName("progressed")
  record Progressed(int done) implements TaskEvent {}

  @JsonTypeName("finished")
  record Finished(String summary) implements TaskEvent {}
}
