package io.github.isharafe.authorization.demo.performance;

import io.github.isharafe.authorization.observability.MicrometerAuthorizationObservation;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Profile("performance")
@Configuration(proxyBeanMethods = false)
class DemoPerformanceConfiguration {
  @Bean
  DemoPerformanceMeasurements demoPerformanceMeasurements(MeterRegistry registry) {
    return new DemoPerformanceMeasurements(registry);
  }

  @Bean
  AuthorizationObservation demoPerformanceObservation(
      MeterRegistry registry, DemoPerformanceMeasurements measurements) {
    return new DemoPerformanceObservation(
        new MicrometerAuthorizationObservation(registry), measurements);
  }

  @Bean
  FilterRegistrationBean<DemoPerformanceFilter> demoPerformanceFilter(
      DemoPerformanceMeasurements measurements) {
    FilterRegistrationBean<DemoPerformanceFilter> registration =
        new FilterRegistrationBean<>(new DemoPerformanceFilter(measurements));
    registration.setName("authorizationPerformanceFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  static BeanPostProcessor performanceDataSourceProxy(
      ObjectProvider<DemoPerformanceMeasurements> measurements) {
    QueryExecutionListener listener = new JdbcMeasurementListener(measurements);
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        if (!(bean instanceof DataSource dataSource) || bean instanceof ProxyDataSource) return bean;
        return ProxyDataSourceBuilder.create(dataSource)
            .name("authorization-performance")
            .listener(listener)
            .build();
      }
    };
  }

  private record JdbcMeasurementListener(ObjectProvider<DemoPerformanceMeasurements> measurements)
      implements QueryExecutionListener {
    private static final String STARTED_NANOS =
        JdbcMeasurementListener.class.getName() + ".startedNanos";

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
      executionInfo.addCustomValue(STARTED_NANOS, System.nanoTime());
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
      Duration duration = elapsed(executionInfo);
      int statements =
          queryInfoList.stream()
              .mapToInt(query -> Math.max(1, query.getParametersList().size()))
              .sum();
      measurements.getObject().recordJdbc(
          operation(queryInfoList),
          executionInfo.isSuccess() ? "success" : "failure",
          statements,
          duration);
    }

    private Duration elapsed(ExecutionInfo executionInfo) {
      Long started = executionInfo.getCustomValue(STARTED_NANOS, Long.class);
      return started == null
          ? Duration.ofMillis(executionInfo.getElapsedTime())
          : Duration.ofNanos(System.nanoTime() - started);
    }

    private String operation(List<QueryInfo> queries) {
      String operation = null;
      for (QueryInfo query : queries) {
        String current = operation(query.getQuery());
        if (operation != null && !operation.equals(current)) return "mixed";
        operation = current;
      }
      return operation == null ? "other" : operation;
    }

    private String operation(String sql) {
      if (sql == null) return "other";
      String normalized = sql.stripLeading().toLowerCase(java.util.Locale.ROOT);
      if (normalized.startsWith("select")) return "select";
      if (normalized.startsWith("insert")) return "insert";
      if (normalized.startsWith("update")) return "update";
      if (normalized.startsWith("delete")) return "delete";
      return "other";
    }
  }
}
