package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.TypeRef;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Date and time types. Their JSON shape depends on the mapper configuration (ISO strings by default
 * in Jackson 3, numbers or arrays when timestamps are enabled), so it is found by writing a sample
 * value with the mapper itself.
 */
final class Temporals {

  private static final Map<Class<?>, Object> SAMPLES =
      Map.ofEntries(
          Map.entry(Instant.class, Instant.EPOCH),
          Map.entry(LocalDate.class, LocalDate.of(2000, 1, 2)),
          Map.entry(LocalDateTime.class, LocalDateTime.of(2000, 1, 2, 3, 4)),
          Map.entry(LocalTime.class, LocalTime.of(3, 4)),
          Map.entry(
              OffsetDateTime.class, OffsetDateTime.of(2000, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)),
          Map.entry(OffsetTime.class, OffsetTime.of(3, 4, 5, 0, ZoneOffset.UTC)),
          Map.entry(ZonedDateTime.class, ZonedDateTime.of(2000, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)),
          Map.entry(Year.class, Year.of(2000)),
          Map.entry(YearMonth.class, YearMonth.of(2000, 1)),
          Map.entry(MonthDay.class, MonthDay.of(1, 2)),
          Map.entry(Duration.class, Duration.ofSeconds(90)),
          Map.entry(Period.class, Period.ofDays(3)),
          Map.entry(ZoneId.class, ZoneId.of("UTC")),
          Map.entry(ZoneOffset.class, ZoneOffset.UTC),
          Map.entry(Date.class, new Date(0)));

  private Temporals() {}

  /** The JSON type of a date or time class, or {@code null} when the class is not one. */
  static @Nullable TypeRef of(Class<?> type, ObjectMapper mapper) {
    Object sample = SAMPLES.get(type);
    if (sample == null && ZoneId.class.isAssignableFrom(type)) {
      sample = SAMPLES.get(ZoneId.class);
    }
    if (sample == null) {
      return null;
    }
    return shapeOf(mapper.valueToTree(sample));
  }

  private static TypeRef shapeOf(JsonNode json) {
    if (json.isString()) {
      return TypeRef.STRING;
    }
    if (json.isNumber()) {
      return TypeRef.NUMBER;
    }
    if (json.isArray()) {
      return new TypeRef.ArrayOf(TypeRef.NUMBER); // [2000, 1, 2] with WRITE_DATES_AS_TIMESTAMPS
    }
    return TypeRef.UNKNOWN;
  }
}
