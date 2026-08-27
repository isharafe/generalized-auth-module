package com.example.authorization.admin.config;

import com.example.authorization.admin.api.AdminApiExceptionHandler;
import com.example.authorization.admin.api.AuthorizationAdminController;
import com.example.authorization.admin.api.AuthorizationCapabilitiesController;
import com.example.authorization.admin.seed.FrameworkAdminSeedContributor;
import com.example.authorization.admin.service.AuthorizationAdminService;
import com.example.authorization.config.AuthorizationAutoConfiguration;
import com.example.authorization.config.AuthorizationProperties;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuthorizationAutoConfiguration.class)
@EnableConfigurationProperties(AuthorizationAdminProperties.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAdminAutoConfiguration {
  @Bean
  FrameworkAdminSeedContributor frameworkAdminSeedContributor(
      AuthorizationAdminProperties properties) {
    return new FrameworkAdminSeedContributor(
        properties.getApi().getBasePath(), properties.getUi().getBasePath());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
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
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationAdminController authorizationAdminController(
      AuthorizationAdminService service) {
    return new AuthorizationAdminController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationCapabilitiesController authorizationCapabilitiesController(
      AuthorizationProperties properties,
      IdentitySynchronizationProvider synchronization) {
    return new AuthorizationCapabilitiesController(properties, synchronization);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AdminApiExceptionHandler adminApiExceptionHandler() {
    return new AdminApiExceptionHandler();
  }
}
