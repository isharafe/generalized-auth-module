package com.example.authorization.config;

import com.example.authorization.audit.DatabaseAuthorizationAuditPublisher;
import com.example.authorization.cache.*;
import com.example.authorization.engine.AuthorizationEngine;
import com.example.authorization.persistence.repository.*;
import com.example.authorization.persistence.service.*;
import com.example.authorization.security.*;
import com.example.authorization.seed.*;
import com.example.authorization.spi.*;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@EnableConfigurationProperties(AuthorizationProperties.class)
@ConditionalOnProperty(
    prefix = "authorization",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(AuthorizationPersistenceConfiguration.class)
public class AuthorizationAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  PermissionMatcher permissionMatcher(ObjectProvider<ResourcePatternMatcher> strategies) {
    return new DefaultPermissionMatcher(strategies.orderedStream().toList());
  }

  @Bean
  @ConditionalOnMissingBean
  SpringAuthenticationIdentityResolver springAuthenticationIdentityResolver() {
    return new DefaultSpringAuthenticationIdentityResolver();
  }

  @Bean
  @ConditionalOnMissingBean(EntitlementProvider.class)
  EntitlementProvider entitlementProvider(
      UserRepository repository, AuthorizationProperties properties) {
    EntitlementProvider provider = new DatabaseEntitlementProvider(repository);
    AuthorizationProperties.CacheRegion cache = properties.getCache().getEntitlements();
    return cache.isEnabled() ? new CachingEntitlementProvider(provider, cache.getTtl()) : provider;
  }

  @Bean
  @ConditionalOnMissingBean(ResourceRuleProvider.class)
  ResourceRuleProvider resourceRuleProvider(
      ResourceRuleRepository repository, AuthorizationProperties properties) {
    ResourceRuleProvider provider = new DatabaseResourceRuleProvider(repository);
    AuthorizationProperties.CacheRegion cache = properties.getCache().getResourceRules();
    return cache.isEnabled() ? new CachingResourceRuleProvider(provider, cache.getTtl()) : provider;
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationCacheInvalidator.class)
  AuthorizationCacheInvalidator authorizationCacheInvalidator(
      ObjectProvider<EntitlementProvider> entitlements,
      ObjectProvider<ResourceRuleProvider> rules) {
    return new DefaultAuthorizationCacheInvalidator(entitlements, rules);
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationAuditPublisher.class)
  AuthorizationAuditPublisher authorizationAuditPublisher(AuditEventRepository repository) {
    return new DatabaseAuthorizationAuditPublisher(repository);
  }

  @Bean
  @ConditionalOnMissingBean(ExternalAuthorityMapper.class)
  ExternalAuthorityMapper externalAuthorityMapper(ExternalAuthorityMappingRepository repository) {
    return new DatabaseExternalAuthorityMapper(repository);
  }

  @Bean
  @ConditionalOnMissingBean(IdentitySynchronizationProvider.class)
  IdentitySynchronizationProvider identitySynchronizationProvider() {
    return new IdentitySynchronizationProvider() {};
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationEngine authorizationEngine(
      ResourceRuleProvider rules, EntitlementProvider entitlements, PermissionMatcher matcher) {
    return new AuthorizationEngine(rules, entitlements, matcher);
  }

  @Bean
  @ConditionalOnMissingBean
  DynamicRequestAuthorizationManager dynamicRequestAuthorizationManager(
      AuthorizationEngine engine,
      SpringAuthenticationIdentityResolver resolver,
      AuthorizationAuditPublisher audit) {
    return new DynamicRequestAuthorizationManager(engine, resolver, audit);
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationServiceUnavailableHandler authorizationServiceUnavailableHandler() {
    return new AuthorizationServiceUnavailableHandler();
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationSeedValidator authorizationSeedValidator(PermissionMatcher matcher) {
    return new AuthorizationSeedValidator(matcher);
  }

  @Bean
  FrameworkAdminSeedContributor frameworkAdminSeedContributor() {
    return new FrameworkAdminSeedContributor();
  }

  @Bean
  AuthorizationSeedLoader authorizationSeedLoader(ResourceLoader resources) {
    return new AuthorizationSeedLoader(resources);
  }

  @Bean
  AuthorizationSeedService authorizationSeedService(
      PermissionRepository permissions,
      PermissionGroupRepository groups,
      RoleRepository roles,
      ResourceRuleRepository rules,
      UserRepository users,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      PendingUserAssignmentRepository pending,
      SeedHistoryRepository history,
      AuthorizationCacheInvalidator cache,
      ObjectProvider<PendingUserAssignmentResolver> pendingResolver,
      AuthorizationSeedValidator validator) {
    return new AuthorizationSeedService(
        permissions,
        groups,
        roles,
        rules,
        users,
        userRoles,
        userGroups,
        pending,
        history,
        cache,
        pendingResolver,
        validator);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "authorization.seed",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationSeedRunner authorizationSeedRunner(
      AuthorizationSeedLoader loader,
      List<AuthorizationSeedContributor> contributors,
      AuthorizationSeedService service,
      AuthorizationProperties properties) {
    return new AuthorizationSeedRunner(
        loader,
        properties.getSeed().getLocations(),
        contributors,
        service,
        properties.getSeed().isFailOnError());
  }

  @Bean
  ApplicationRunner authorizationSourceGuard(
      AuthorizationProperties properties, IdentitySynchronizationProvider synchronizationProvider) {
    return args -> {
      if (!"DENY".equalsIgnoreCase(properties.getDefaultDecision()))
        throw new IllegalStateException(
            "authorization.default-decision must be DENY to preserve fail-closed behavior");
      if (!properties.getSource().equalsIgnoreCase("database")
          && !synchronizationProvider.supported())
        throw new IllegalStateException(
            "authorization.source="
                + properties.getSource()
                + " requires its optional integration module; authorization-core supports database"
                + " by default");
    };
  }
}
