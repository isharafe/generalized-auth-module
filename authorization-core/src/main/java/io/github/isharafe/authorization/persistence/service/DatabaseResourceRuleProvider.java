package io.github.isharafe.authorization.persistence.service;

import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.persistence.entity.ResourceRuleEntity;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.spi.ResourceRuleProvider;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseResourceRuleProvider implements ResourceRuleProvider {
  private final ResourceRuleRepository repository;

  @Override
  @Transactional(readOnly = true)
  public List<ResourceRule> findEnabledRules() {
    return repository.findByEnabledTrue().stream().map(ResourceRuleEntity::toDomain).toList();
  }
}
