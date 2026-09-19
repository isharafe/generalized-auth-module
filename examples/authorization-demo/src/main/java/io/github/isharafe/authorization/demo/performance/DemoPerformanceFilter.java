package io.github.isharafe.authorization.demo.performance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
final class DemoPerformanceFilter extends OncePerRequestFilter {
  private final DemoPerformanceMeasurements measurements;

  @Override
  protected void doFilterInternal(
          @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    measurements.begin();
    long started = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      DemoPerformanceMeasurements.RequestMeasurement value = measurements.finish();
      log.info(
          "authorizationPerformance correlationId={} method={} path={} status={} totalMs={} jdbcCalls={} sqlStatements={} jdbcMs={} externalCalls={} externalMs={} externalOperations={}",
          correlationId(),
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          millis(Duration.ofNanos(System.nanoTime() - started)),
          value.jdbcCalls(),
          value.jdbcStatements(),
          millis(value.jdbcDuration()),
          value.externalCalls(),
          millis(value.externalDuration()),
          value.externalCallsByOperation());
    }
  }

  private String correlationId() {
    String value = MDC.get("correlationId");
    if (value == null || value.isBlank()) value = MDC.get("traceId");
    return value == null || value.isBlank() ? "-" : value;
  }

  private double millis(Duration duration) {
    return duration.toNanos() / 1_000_000.0;
  }
}
