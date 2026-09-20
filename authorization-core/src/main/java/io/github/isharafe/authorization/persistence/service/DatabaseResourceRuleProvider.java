package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.engine.AuthorizationInfrastructureException;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.spi.ResourceRuleProvider;
import jakarta.persistence.PersistenceException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class DatabaseResourceRuleProvider implements ResourceRuleProvider {
  private final ResourceRuleRepository repository;

  @Override
  @Transactional(readOnly = true)
  public List<ResourceRule> findEnabledRules() {
    try {
      return repository.findByEnabledTrue().stream().map(ResourceRuleEntity::toDomain).toList();
    } catch (DataAccessException | PersistenceException failure) {
      throw new AuthorizationInfrastructureException("Unable to load resource rules", failure);
    }
  }
}
