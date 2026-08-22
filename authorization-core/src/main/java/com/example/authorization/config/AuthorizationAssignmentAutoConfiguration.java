package com.example.authorization.config;

import com.example.authorization.persistence.repository.*;
import com.example.authorization.persistence.service.PendingUserAssignmentResolver;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuthorizationAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAssignmentAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  PendingUserAssignmentResolver pendingUserAssignmentResolver(
      UserRepository users,
      RoleRepository roles,
      PermissionGroupRepository groups,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      PendingUserAssignmentRepository pending,
      AuthorizationCacheInvalidator cache) {
    return new PendingUserAssignmentResolver(
        users, roles, groups, userRoles, userGroups, pending, cache);
  }
}
