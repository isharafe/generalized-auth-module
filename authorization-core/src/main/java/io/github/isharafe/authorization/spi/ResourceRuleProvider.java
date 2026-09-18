package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.ResourceRule;
import java.util.List;

public interface ResourceRuleProvider {
  List<ResourceRule> findEnabledRules();
}
