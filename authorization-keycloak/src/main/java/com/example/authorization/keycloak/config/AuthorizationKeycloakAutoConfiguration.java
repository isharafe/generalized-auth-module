package com.example.authorization.keycloak.config;

import com.example.authorization.config.AuthorizationAutoConfiguration;
import com.example.authorization.keycloak.client.HttpKeycloakAdminClient;
import com.example.authorization.keycloak.client.KeycloakAdminClient;
import com.example.authorization.keycloak.sync.KeycloakIdentitySynchronizationProvider;
import com.example.authorization.keycloak.sync.KeycloakSynchronizationScheduler;
import com.example.authorization.persistence.repository.PermissionGroupRepository;
import com.example.authorization.persistence.repository.RoleRepository;
import com.example.authorization.persistence.repository.SyncStateRepository;
import com.example.authorization.persistence.repository.UserPermissionGroupRepository;
import com.example.authorization.persistence.repository.UserRepository;
import com.example.authorization.persistence.repository.UserRoleRepository;
import com.example.authorization.persistence.service.PendingUserAssignmentResolver;
import com.example.authorization.spi.AuthorizationAuditPublisher;
import com.example.authorization.spi.AuthorizationCacheInvalidator;
import com.example.authorization.spi.ExternalAuthorityMapper;
import com.example.authorization.spi.IdentitySynchronizationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
