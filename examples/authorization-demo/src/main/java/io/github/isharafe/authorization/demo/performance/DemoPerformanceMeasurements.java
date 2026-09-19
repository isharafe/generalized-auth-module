package io.github.isharafe.authorization.demo.performance;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class DemoPerformanceMeasurements {
  private final MeterRegistry registry;
  private final ThreadLocal<MutableMeasurement> current = new ThreadLocal<>();
  private volatile RequestMeasurement lastCompleted = RequestMeasurement.empty();

  DemoPerformanceMeasurements(MeterRegistry registry) {
    this.registry = registry;
  }

  void begin() {
    current.set(new MutableMeasurement());
  }

  RequestMeasurement finish() {
    MutableMeasurement measurement = current.get();
    current.remove();
    RequestMeasurement completed =
        measurement == null ? RequestMeasurement.empty() : measurement.snapshot();
    lastCompleted = completed;
    return completed;
  }

  RequestMeasurement lastCompleted() {
    return lastCompleted;
  }

  void recordJdbc(String operation, String result, int statements, Duration duration) {
    MutableMeasurement measurement = current.get();
    if (measurement == null) return;
    measurement.jdbcCalls++;
    measurement.jdbcStatements += statements;
    measurement.jdbcNanos += duration.toNanos();
    Tags tags = Tags.of("operation", operation, "result", result);
    registry.counter("authorization.jdbc.calls", tags).increment();
    registry.counter("authorization.jdbc.statements", tags).increment(statements);
    registry.timer("authorization.jdbc.call.duration", tags).record(duration);
  }

  void recordExternal(String system, String operation, Duration duration) {
    MutableMeasurement measurement = current.get();
    if (measurement == null) return;
    measurement.externalCalls++;
    measurement.externalNanos += duration.toNanos();
    measurement.externalCallsByOperation.merge(system + ":" + operation, 1L, Long::sum);
  }

  record RequestMeasurement(
      long jdbcCalls,
      long jdbcStatements,
      Duration jdbcDuration,
      long externalCalls,
      Duration externalDuration,
      Map<String, Long> externalCallsByOperation) {
    static RequestMeasurement empty() {
      return new RequestMeasurement(0, 0, Duration.ZERO, 0, Duration.ZERO, Map.of());
    }
  }

  private static final class MutableMeasurement {
    private long jdbcCalls;
    private long jdbcStatements;
    private long jdbcNanos;
    private long externalCalls;
    private long externalNanos;
    private final Map<String, Long> externalCallsByOperation = new LinkedHashMap<>();

    private RequestMeasurement snapshot() {
      return new RequestMeasurement(
          jdbcCalls,
          jdbcStatements,
          Duration.ofNanos(jdbcNanos),
          externalCalls,
          Duration.ofNanos(externalNanos),
          Map.copyOf(externalCallsByOperation));
    }
  }
}
