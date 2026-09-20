package io.github.isharafe.authorization.admin.config;

import io.github.isharafe.authorization.admin.api.AdminApiExceptionHandler;
import io.github.isharafe.authorization.admin.api.AuthorizationCatalogController;
import io.github.isharafe.authorization.admin.api.AuthorizationCapabilitiesController;
import io.github.isharafe.authorization.admin.api.AuthorizationDataTransferController;
import io.github.isharafe.authorization.admin.api.AuthorizationOperationsController;
import io.github.isharafe.authorization.admin.api.AuthorizationUserController;
import io.github.isharafe.authorization.admin.api.UrlResourceInventoryController;
import io.github.isharafe.authorization.admin.seed.AdminSeedResourceContributor;
import io.github.isharafe.authorization.admin.service.AdminChangePublisher;
import io.github.isharafe.authorization.admin.service.AuthorizationCatalogAdminService;
import io.github.isharafe.authorization.admin.service.AuthorizationDataTransferService;
import io.github.isharafe.authorization.admin.service.AuthorizationOperationsAdminService;
import io.github.isharafe.authorization.admin.service.AuthorizationUserAdminService;
import io.github.isharafe.authorization.admin.service.UrlResourceInventoryService;
import io.github.isharafe.authorization.config.AuthorizationAutoConfiguration;
import io.github.isharafe.authorization.config.AuthorizationProperties;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.persistence.repository.AuditEventRepository;
import io.github.isharafe.authorization.persistence.repository.ExternalAuthorityMappingRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.PermissionRepository;
import io.github.isharafe.authorization.persistence.repository.PendingUserAssignmentRepository;
import io.github.isharafe.authorization.persistence.repository.ResourceRuleRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.security.SpringAuthenticationIdentityResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.EntitlementProvider;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import io.github.isharafe.authorization.spi.PermissionMatcher;
import io.github.isharafe.authorization.spi.UrlSecurityPolicyContributor;
import io.github.isharafe.authorization.util.AfterCommitExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

@AutoConfiguration(after = AuthorizationAutoConfiguration.class)
@EnableConfigurationProperties(AuthorizationAdminProperties.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationAdminAutoConfiguration {
  @Bean
  AdminChangePublisher adminChangePublisher(
      AuthorizationCacheInvalidator cache,
      AuthorizationAuditPublisher audit,
      SpringAuthenticationIdentityResolver identityResolver,
      AfterCommitExecutor afterCommit) {
    return new AdminChangePublisher(cache, audit, identityResolver, afterCommit);
  }

  @Bean
  AdminSeedResourceContributor adminSeedResourceContributor(
      AuthorizationAdminProperties properties) {
    return new AdminSeedResourceContributor(
        properties.getApi().getBasePath(), properties.getUi().getBasePath());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationCatalogAdminService authorizationCatalogAdminService(
      RoleRepository roles,
      PermissionGroupRepository groups,
      PermissionRepository permissions,
      ResourceRuleRepository rules,
      UserRoleRepository userRoles,
      PermissionMatcher matcher,
      AdminChangePublisher changes) {
    return new AuthorizationCatalogAdminService(
        roles, groups, permissions, rules, userRoles, matcher, changes);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationUserAdminService authorizationUserAdminService(
      RoleRepository roles,
      PermissionGroupRepository groups,
      UserRepository users,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      EntitlementProvider entitlements,
      AuthorizationEngine engine,
      PermissionMatcher matcher,
      SpringAuthenticationIdentityResolver identityResolver,
      AdminChangePublisher changes) {
    return new AuthorizationUserAdminService(
        roles,
        groups,
        users,
        userRoles,
        userGroups,
        entitlements,
        engine,
        matcher,
        identityResolver,
        changes);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationOperationsAdminService authorizationOperationsAdminService(
      RoleRepository roles,
      PermissionGroupRepository groups,
      ExternalAuthorityMappingRepository externalMappings,
      AuditEventRepository auditEvents,
      IdentitySynchronizationProvider synchronization,
      AdminChangePublisher changes) {
    return new AuthorizationOperationsAdminService(
        roles, groups, externalMappings, auditEvents, synchronization, changes);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationCatalogController authorizationCatalogController(
      AuthorizationCatalogAdminService service) {
    return new AuthorizationCatalogController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationUserController authorizationUserController(AuthorizationUserAdminService service) {
    return new AuthorizationUserController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationOperationsController authorizationOperationsController(
      AuthorizationOperationsAdminService service) {
    return new AuthorizationOperationsController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  UrlResourceInventoryService urlResourceInventoryService(
      ObjectProvider<RequestMappingInfoHandlerMapping> handlerMappings,
      ResourceRuleRepository rules,
      PermissionMatcher matcher,
      ObjectProvider<UrlSecurityPolicyContributor> securityPolicies) {
    return new UrlResourceInventoryService(handlerMappings, rules, matcher, securityPolicies);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  UrlResourceInventoryController urlResourceInventoryController(
      UrlResourceInventoryService service) {
    return new UrlResourceInventoryController(service);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationDataTransferService authorizationDataTransferService(
      PermissionRepository permissions,
      PermissionGroupRepository groups,
      RoleRepository roles,
      ResourceRuleRepository rules,
      UserRepository users,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      ExternalAuthorityMappingRepository externalMappings,
      PendingUserAssignmentRepository pendingAssignments,
      PermissionMatcher matcher,
      AuthorizationCacheInvalidator cache,
      AuthorizationAuditPublisher audit,
      SpringAuthenticationIdentityResolver identityResolver,
      EntityManager entityManager,
      AdminChangePublisher changes) {
    return new AuthorizationDataTransferService(
        permissions,
        groups,
        roles,
        rules,
        users,
        userRoles,
        userGroups,
        externalMappings,
        pendingAssignments,
        matcher,
        cache,
        audit,
        identityResolver,
        entityManager,
        changes);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.admin.api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationDataTransferController authorizationDataTransferController(
      AuthorizationDataTransferService service) {
    return new AuthorizationDataTransferController(service);
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
