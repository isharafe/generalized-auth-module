package com.example.authorization.config;

import com.example.authorization.admin.api.AdminApiExceptionHandler;
import com.example.authorization.admin.api.AuthorizationAdminController;
import com.example.authorization.admin.api.AuthorizationCapabilitiesController;
import com.example.authorization.admin.service.AuthorizationAdminService;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.persistence.repository.AuditEventRepository;
import com.example.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.PermissionRepository;
import com.example.authorization.persistence.repository.ResourceRuleRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.security.SpringAuthenticationIdentityResolver;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.example.authorization.spi.EntitlementProvider;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import com.example.authorization.spi.PermissionMatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuthorizationAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "authorization.admin.api",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAdminAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  AuthorizationAdminService authorizationAdminService(
      RoleRepository roles,
      PermissionGroupRepository groups,
      PermissionRepository permissions,
      ResourceRuleRepository rules,
      UserRepository users,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      ExternalAuthorityMappingRepository externalMappings,
      AuditEventRepository auditEvents,
      EntitlementProvider entitlements,
      AuthorizationEngine engine,
      PermissionMatcher matcher,
      IdentitySynchronizationProvider synchronization,
      AuthorizationCacheInvalidator cache,
      AuthorizationAuditPublisher audit,
      SpringAuthenticationIdentityResolver identityResolver) {
    return new AuthorizationAdminService(
        roles,
        groups,
        permissions,
        rules,
        users,
        userRoles,
        userGroups,
        externalMappings,
        auditEvents,
        entitlements,
        engine,
        matcher,
        synchronization,
        cache,
        audit,
        identityResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationAdminController authorizationAdminController(
      AuthorizationAdminService service) {
    return new AuthorizationAdminController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationCapabilitiesController authorizationCapabilitiesController(
      AuthorizationProperties properties,
      IdentitySynchronizationProvider synchronization) {
    return new AuthorizationCapabilitiesController(properties, synchronization);
  }

  @Bean
  @ConditionalOnMissingBean
  AdminApiExceptionHandler adminApiExceptionHandler() {
    return new AdminApiExceptionHandler();
  }
}
