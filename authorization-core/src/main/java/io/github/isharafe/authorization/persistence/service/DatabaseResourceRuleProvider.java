package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.spi.ResourceRuleProvider;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import java.time.Duration;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class DatabaseResourceRuleProvider implements ResourceRuleProvider {
  private final ResourceRuleRepository repository;
  private final AuthorizationObservation observation;

  public DatabaseResourceRuleProvider(
      ResourceRuleRepository repository, AuthorizationObservation observation) {
    this.repository = repository;
    this.observation = observation;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ResourceRule> findEnabledRules() {
    long started = System.nanoTime();
    String result = "success";
    try {
      return repository.findByEnabledTrue().stream().map(ResourceRuleEntity::toDomain).toList();
    } catch (RuntimeException exception) {
      result = "failure";
      throw exception;
    } finally {
      observation.recordPersistenceOperation(
          "resource_rules_load", result, Duration.ofNanos(System.nanoTime() - started));
    }
  }
}
