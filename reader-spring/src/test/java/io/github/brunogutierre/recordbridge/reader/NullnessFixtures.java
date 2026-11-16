package io.github.brunogutierre.recordbridge.reader;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/** Fixtures for nullness, optional values and dates. This package is {@code @NullMarked}. */
@SuppressWarnings("unused")
final class NullnessFixtures {

  private NullnessFixtures() {}

  record Marked(
      String name,
      @Nullable String nickname,
      List<@Nullable String> aliases,
      Optional<String> middle,
      int age,
      @Nullable Integer score) {}

  @NullUnmarked
  record Unmarked(String name, @Nullable String nickname, @NonNull String id, int age) {}

  @NullUnmarked
  static final class LegacyBean {
    @io.github.brunogutierre.recordbridge.reader.legacy.Nullable public String getNote() {
      return "";
    }
  }

  @JsonInclude(NON_NULL)
  record Sparse(@Nullable String nickname, Optional<String> middle, String name) {}

  @JsonInclude(NON_ABSENT)
  record Absent(@Nullable String nickname, Optional<String> middle) {}

  record PerProperty(@JsonInclude(NON_EMPTY) List<String> tags, String name) {}

  record Box<T>(@Nullable T value, T required) {}

  record Uses(Box<@Nullable String> loose, Box<String> strict) {}

  record Times(
      Instant at,
      LocalDate day,
      LocalTime time,
      ZonedDateTime zoned,
      Duration took,
      Date legacy,
      DayOfWeek weekday,
      OptionalInt maybe) {}
}
