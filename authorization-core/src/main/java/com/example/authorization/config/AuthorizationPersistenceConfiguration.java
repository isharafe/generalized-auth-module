package com.example.authorization.config;

import com.example.authorization.persistence.entity.UserEntity;
import com.example.authorization.persistence.repository.UserRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
class AuthorizationPersistenceConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "authorization.database.migration",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  FlywayMigrationInitializer authorizationFlywayInitializer(
      DataSource dataSource, AuthorizationProperties properties) {
    AuthorizationProperties.Migration migration = properties.getDatabase().getMigration();
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations(migration.getLocation())
            .table(migration.getHistoryTable())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
    return new FlywayMigrationInitializer(flyway);
  }
}
