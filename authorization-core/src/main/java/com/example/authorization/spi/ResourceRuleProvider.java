package com.example.authorization.spi;

import com.example.authorization.domain.ResourceRule;
import java.util.List;

public interface ResourceRuleProvider {
  List<ResourceRule> findEnabledRules();
}
