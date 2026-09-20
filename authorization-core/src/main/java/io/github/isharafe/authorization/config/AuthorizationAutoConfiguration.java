package io.github.isharafe.authorization.config;

import io.github.isharafe.authorization.audit.DatabaseAuthorizationAuditPublisher;
import io.github.isharafe.authorization.cache.*;
import io.github.isharafe.authorization.engine.AuthorizationEngine;
import io.github.isharafe.authorization.observability.MicrometerAuthorizationObservation;
import io.github.isharafe.authorization.observability.NoOpAuthorizationObservation;
import io.github.isharafe.authorization.persistence.repository.*;
import io.github.isharafe.authorization.persistence.service.*;
import io.github.isharafe.authorization.security.*;
import io.github.isharafe.authorization.seed.*;
import io.github.isharafe.authorization.spi.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.EnableScheduling;

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
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean(AuthorizationObservation.class)
  AuthorizationObservation micrometerAuthorizationObservation(MeterRegistry registry) {
    return new MicrometerAuthorizationObservation(registry);
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationObservation.class)
  AuthorizationObservation authorizationObservation() {
    return new NoOpAuthorizationObservation();
  }

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
      UserRepository repository,
      AuthorizationProperties properties,
      AuthorizationObservation observation) {
    EntitlementProvider provider = new DatabaseEntitlementProvider(repository);
    AuthorizationProperties.CacheRegion cache = properties.getCache().getEntitlements();
    return cache.isEnabled()
        ? new CachingEntitlementProvider(provider, cache.getTtl(), observation)
        : provider;
  }

  @Bean
  @ConditionalOnMissingBean(ResourceRuleProvider.class)
  ResourceRuleProvider resourceRuleProvider(
      ResourceRuleRepository repository,
      AuthorizationProperties properties,
      AuthorizationObservation observation) {
    ResourceRuleProvider provider = new DatabaseResourceRuleProvider(repository);
    AuthorizationProperties.CacheRegion cache = properties.getCache().getResourceRules();
    return cache.isEnabled()
        ? new CachingResourceRuleProvider(provider, cache.getTtl(), observation)
        : provider;
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationInvalidationPublisher.class)
  @ConditionalOnProperty(
      prefix = "authorization.distributed-invalidation",
      name = "enabled",
      havingValue = "true")
  AuthorizationInvalidationPublisher databaseAuthorizationInvalidationPublisher(
      CacheInvalidationRepository repository) {
    return new DatabaseAuthorizationInvalidationPublisher(repository);
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationInvalidationPublisher.class)
  AuthorizationInvalidationPublisher authorizationInvalidationPublisher() {
    return new NoOpAuthorizationInvalidationPublisher();
  }

  @Bean
  @ConditionalOnMissingBean(AuthorizationCacheInvalidator.class)
  PublishingAuthorizationCacheInvalidator authorizationCacheInvalidator(
      ObjectProvider<EntitlementProvider> entitlements,
      ObjectProvider<ResourceRuleProvider> rules,
      AuthorizationInvalidationPublisher publisher,
      AuthorizationObservation observation,
      AuthorizationProperties properties,
      ObjectProvider<Clock> clocks) {
    return new PublishingAuthorizationCacheInvalidator(
        new DefaultAuthorizationCacheInvalidator(entitlements, rules),
        publisher,
        observation,
        properties.getDistributedInvalidation().getInstanceId(),
        clocks.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnBean(PublishingAuthorizationCacheInvalidator.class)
  @ConditionalOnProperty(
      prefix = "authorization.distributed-invalidation",
      name = "enabled",
      havingValue = "true")
  DatabaseAuthorizationInvalidationReceiver databaseAuthorizationInvalidationReceiver(
      CacheInvalidationRepository repository,
      PublishingAuthorizationCacheInvalidator invalidator,
      AuthorizationProperties properties,
      ObjectProvider<Clock> clocks) {
    return new DatabaseAuthorizationInvalidationReceiver(
        repository, invalidator, properties, clocks.getIfAvailable(Clock::systemUTC));
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
  @ConditionalOnProperty(
      prefix = "authorization",
      name = "source",
      havingValue = "database",
      matchIfMissing = true)
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
  AuthorizationService authorizationService(
      AuthorizationEngine engine,
      SpringAuthenticationIdentityResolver resolver,
      AuthorizationAuditPublisher audit,
      AuthorizationObservation observation) {
    return new AuthorizationService(engine, resolver, audit, observation);
  }

  @Bean
  @ConditionalOnMissingBean
  DynamicRequestAuthorizationManager dynamicRequestAuthorizationManager(
      AuthorizationService authorization) {
    return new DynamicRequestAuthorizationManager(authorization);
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationServiceUnavailableHandler authorizationServiceUnavailableHandler() {
    return new AuthorizationServiceUnavailableHandler();
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnProperty(
      prefix = "authorization.ui-api",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean
  CurrentUserUiPermissionsEndpoint currentUserUiPermissionsEndpoint(
      SpringAuthenticationIdentityResolver identities, EntitlementProvider entitlements) {
    return new CurrentUserUiPermissionsEndpoint(identities, entitlements);
  }

  @Bean
  @ConditionalOnMissingBean
  AuthorizationSeedValidator authorizationSeedValidator(PermissionMatcher matcher) {
    return new AuthorizationSeedValidator(matcher);
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
      ExternalAuthorityMappingRepository externalMappings,
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
        externalMappings,
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
  AuthorizationSeedInitializationService authorizationSeedInitializationService(
      SyncStateRepository syncStates, AuthorizationSeedService seeds) {
    return new AuthorizationSeedInitializationService(syncStates, seeds);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "authorization.seed",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  AuthorizationSeedRunner authorizationSeedRunner(
      AuthorizationSeedLoader loader,
      List<AuthorizationSeedResourceContributor> resourceContributors,
      List<AuthorizationSeedContributor> contributors,
      AuthorizationSeedInitializationService service,
      AuthorizationProperties properties) {
    return new AuthorizationSeedRunner(
        loader,
        properties.getSeed().getLocations(),
        resourceContributors,
        contributors,
        service,
        properties.getSeed().isFailOnError());
  }

  @Bean
  @ConditionalOnMissingBean(IdentityChangeEventProcessor.class)
  IdentityChangeEventProcessor identityChangeEventProcessor(
      IdentityChangeEventRepository events,
      ObjectProvider<IdentitySynchronizationProvider> synchronizationProviders,
      AuthorizationProperties properties,
      ObjectProvider<Clock> clocks,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      AuthorizationObservation observation) {
    return new DatabaseIdentityChangeEventProcessor(
        events,
        synchronizationProviders,
        properties,
        clocks.getIfAvailable(Clock::systemUTC),
        transactionManager,
        observation);
  }

  @Bean
  ApplicationRunner authorizationSourceGuard(
      AuthorizationProperties properties,
      ObjectProvider<IdentitySynchronizationProvider> synchronizationProviders) {
    return args -> {
      if (!"DENY".equalsIgnoreCase(properties.getDefaultDecision()))
        throw new IllegalStateException(
            "authorization.default-decision must be DENY to preserve fail-closed behavior");
      if (properties.getIdentityEvents().getProcessingTimeout() == null
          || properties.getIdentityEvents().getProcessingTimeout().isZero()
          || properties.getIdentityEvents().getProcessingTimeout().isNegative())
        throw new IllegalStateException(
            "authorization.identity-events.processing-timeout must be positive");
      AuthorizationProperties.DistributedInvalidation distributed =
          properties.getDistributedInvalidation();
      if (distributed.getPollInterval() == null
          || distributed.getPollInterval().isZero()
          || distributed.getPollInterval().isNegative())
        throw new IllegalStateException(
            "authorization.distributed-invalidation.poll-interval must be positive");
      if (distributed.getRetention() == null
          || distributed.getRetention().isZero()
          || distributed.getRetention().isNegative())
        throw new IllegalStateException(
            "authorization.distributed-invalidation.retention must be positive");
      if (distributed.getBatchSize() < 1 || distributed.getBatchSize() > 10000)
        throw new IllegalStateException(
            "authorization.distributed-invalidation.batch-size must be between 1 and 10000");
      if (distributed.getInstanceId() == null
          || distributed.getInstanceId().isBlank()
          || distributed.getInstanceId().length() > 100)
        throw new IllegalStateException(
            "authorization.distributed-invalidation.instance-id must contain 1 to 100 characters");
      IdentitySynchronizationProvider synchronizationProvider =
          synchronizationProviders.getIfAvailable();
      if (!properties.getSource().equalsIgnoreCase("database")
          && (synchronizationProvider == null || !synchronizationProvider.supported()))
        throw new IllegalStateException(
            "authorization.source="
                + properties.getSource()
                + " requires its optional integration module and valid configuration;"
                + " authorization-core supports database by default");
    };
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      prefix = "authorization.distributed-invalidation",
      name = "enabled",
      havingValue = "true")
  static class DistributedInvalidationSchedulingConfiguration {}
}
