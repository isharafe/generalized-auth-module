package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.Permission;
import io.github.isharafe.authorization.domain.ProtectedResource;
import io.github.isharafe.authorization.domain.ResourceRule;
import io.github.isharafe.authorization.domain.ResourceType;

public interface PermissionMatcher {
  boolean matches(ResourceRule rule, ProtectedResource resource);

  boolean matches(Permission permission, ProtectedResource resource);

  void validate(ResourceType resourceType, String pattern);

  int compareSpecificity(ResourceRule left, ResourceRule right);
}
