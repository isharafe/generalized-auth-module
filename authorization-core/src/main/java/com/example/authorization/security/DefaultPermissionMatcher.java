package com.example.authorization.security;

import com.example.authorization.domain.Permission;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.domain.ResourceRule;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.spi.PermissionMatcher;
import com.example.authorization.spi.ResourcePatternMatcher;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultPermissionMatcher implements PermissionMatcher {
  private final Map<ResourceType, ResourcePatternMatcher> strategies;

  public DefaultPermissionMatcher() {
    this(List.of());
  }

  public DefaultPermissionMatcher(Collection<ResourcePatternMatcher> overrides) {
    EnumMap<ResourceType, ResourcePatternMatcher> registry = new EnumMap<>(ResourceType.class);
    register(registry, new UrlResourcePatternMatcher());
    register(registry, new UiResourcePatternMatcher());
    Set<ResourceType> overriddenTypes = new HashSet<>();
    for (ResourcePatternMatcher override : overrides) {
      ResourcePatternMatcher value = Objects.requireNonNull(override, "resource pattern matcher");
      if (!overriddenTypes.add(value.resourceType()))
        throw new IllegalArgumentException(
            "Multiple resource pattern matchers for " + value.resourceType());
      registry.put(value.resourceType(), value);
    }
    strategies = Map.copyOf(registry);
  }

  @Override
  public boolean matches(ResourceRule rule, ProtectedResource resource) {
    return rule.enabled()
        && rule.resourceType() == resource.resourceType()
        && strategy(rule.resourceType()).matches(rule.pattern(), resource.pattern());
  }

  @Override
  public boolean matches(Permission permission, ProtectedResource resource) {
    return permission.enabled()
        && permission.resourceType() == resource.resourceType()
        && strategy(permission.resourceType()).matches(permission.pattern(), resource.pattern());
  }

  @Override
  public void validate(ResourceType resourceType, String pattern) {
    strategy(resourceType).validate(pattern);
  }

  @Override
  public int compareSpecificity(ResourceRule left, ResourceRule right) {
    if (left.resourceType() != right.resourceType())
      throw new IllegalArgumentException("Cannot compare rules with different resource types");
    return strategy(left.resourceType()).compareSpecificity(left.pattern(), right.pattern());
  }

  private ResourcePatternMatcher strategy(ResourceType resourceType) {
    ResourcePatternMatcher strategy = strategies.get(resourceType);
    if (strategy == null)
      throw new IllegalArgumentException("No resource pattern matcher for " + resourceType);
    return strategy;
  }

  private static void register(
      Map<ResourceType, ResourcePatternMatcher> registry, ResourcePatternMatcher strategy) {
    registry.put(strategy.resourceType(), strategy);
  }
}
