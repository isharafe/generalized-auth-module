package com.example.authorization.demo;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoApplicationFlywayConfiguration {
  @Bean
  FlywayMigrationInitializer demoApplicationFlywayInitializer(DataSource dataSource) {
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("flyway_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
    return new FlywayMigrationInitializer(flyway);
  }
}
