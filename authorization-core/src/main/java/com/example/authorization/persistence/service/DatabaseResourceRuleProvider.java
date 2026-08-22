package com.example.authorization.persistence.service;

import com.example.authorization.domain.ResourceRule;
import com.example.authorization.persistence.entity.ResourceRuleEntity;
import com.example.authorization.persistence.repository.ResourceRuleRepository;
import com.example.authorization.spi.ResourceRuleProvider;
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
