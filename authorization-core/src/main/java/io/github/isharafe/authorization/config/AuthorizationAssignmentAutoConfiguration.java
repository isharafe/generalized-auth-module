package io.github.isharafe.authorization.config;

import io.github.isharafe.authorization.persistence.repository.*;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.util.AfterCommitExecutor;
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
      AuthorizationCacheInvalidator cache,
      AfterCommitExecutor afterCommit) {
    return new PendingUserAssignmentResolver(
        users, roles, groups, userRoles, userGroups, pending, cache, afterCommit);
  }
}
