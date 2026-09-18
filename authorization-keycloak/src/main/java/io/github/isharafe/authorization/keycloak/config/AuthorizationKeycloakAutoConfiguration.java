package io.github.isharafe.authorization.keycloak.config;

import io.github.isharafe.authorization.config.AuthorizationAutoConfiguration;
import io.github.isharafe.authorization.keycloak.client.HttpKeycloakAdminClient;
import io.github.isharafe.authorization.keycloak.client.KeycloakAdminClient;
import io.github.isharafe.authorization.keycloak.event.KeycloakEventSignatureVerifier;
import io.github.isharafe.authorization.keycloak.event.KeycloakIdentityChangeController;
import io.github.isharafe.authorization.keycloak.sync.KeycloakIdentitySynchronizationProvider;
import io.github.isharafe.authorization.keycloak.sync.KeycloakSynchronizationScheduler;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.SyncStateRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.ExternalAuthorityMapper;
import io.github.isharafe.authorization.spi.IdentityChangeEventProcessor;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = AuthorizationAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(AuthorizationKeycloakProperties.class)
@ConditionalOnProperty(prefix = "authorization", name = "source", havingValue = "keycloak")
@ConditionalOnProperty(
    prefix = "authorization.keycloak",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationKeycloakAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(KeycloakAdminClient.class)
  KeycloakAdminClient keycloakAdminClient(
      AuthorizationKeycloakProperties properties, ObjectProvider<ObjectMapper> mappers) {
    return new HttpKeycloakAdminClient(
        properties, mappers.getIfAvailable(ObjectMapper::new));
  }

  @Bean
  @ConditionalOnMissingBean(IdentitySynchronizationProvider.class)
  KeycloakIdentitySynchronizationProvider keycloakIdentitySynchronizationProvider(
      AuthorizationKeycloakProperties properties,
      KeycloakAdminClient client,
      ExternalAuthorityMapper authorityMapper,
      UserRepository users,
      RoleRepository roles,
      PermissionGroupRepository groups,
      UserRoleRepository userRoles,
      UserPermissionGroupRepository userGroups,
      SyncStateRepository syncStates,
      ObjectProvider<PendingUserAssignmentResolver> pendingResolver,
      AuthorizationCacheInvalidator cache,
      AuthorizationAuditPublisher audit,
      PlatformTransactionManager transactionManager) {
    return new KeycloakIdentitySynchronizationProvider(
        properties,
        client,
        authorityMapper,
        users,
        roles,
        groups,
        userRoles,
        userGroups,
        syncStates,
        pendingResolver,
        cache,
        audit,
        transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.keycloak.sync",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  KeycloakSynchronizationScheduler keycloakSynchronizationScheduler(
      IdentitySynchronizationProvider synchronization) {
    return new KeycloakSynchronizationScheduler(synchronization);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.keycloak.events",
      name = "enabled",
      havingValue = "true")
  KeycloakEventSignatureVerifier keycloakEventSignatureVerifier(
      AuthorizationKeycloakProperties properties, ObjectProvider<Clock> clocks) {
    return new KeycloakEventSignatureVerifier(
        properties, clocks.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.keycloak.events",
      name = "enabled",
      havingValue = "true")
  KeycloakIdentityChangeController keycloakIdentityChangeController(
      AuthorizationKeycloakProperties properties,
      KeycloakEventSignatureVerifier signatures,
      IdentityChangeEventProcessor events,
      ObjectProvider<ObjectMapper> mappers) {
    return new KeycloakIdentityChangeController(
        properties, signatures, events, mappers.getIfAvailable(ObjectMapper::new));
  }
}
