package com.example.authorization.spi;

import com.example.authorization.domain.Permission;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.domain.ResourceRule;
import com.example.authorization.domain.ResourceType;

public interface PermissionMatcher {
  boolean matches(ResourceRule rule, ProtectedResource resource);

  boolean matches(Permission permission, ProtectedResource resource);

  void validate(ResourceType resourceType, String pattern);

  int compareSpecificity(ResourceRule left, ResourceRule right);
}
