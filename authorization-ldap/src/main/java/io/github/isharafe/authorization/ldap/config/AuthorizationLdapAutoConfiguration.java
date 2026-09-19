package io.github.isharafe.authorization.ldap.config;

import io.github.isharafe.authorization.config.AuthorizationAutoConfiguration;
import io.github.isharafe.authorization.ldap.client.JndiLdapDirectoryClient;
import io.github.isharafe.authorization.ldap.client.LdapDirectoryClient;
import io.github.isharafe.authorization.ldap.sync.LdapIdentitySynchronizationProvider;
import io.github.isharafe.authorization.ldap.sync.LdapSynchronizationScheduler;
import io.github.isharafe.authorization.persistence.repository.PermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.RoleRepository;
import io.github.isharafe.authorization.persistence.repository.SyncStateRepository;
import io.github.isharafe.authorization.persistence.repository.UserPermissionGroupRepository;
import io.github.isharafe.authorization.persistence.repository.UserRepository;
import io.github.isharafe.authorization.persistence.repository.UserRoleRepository;
import io.github.isharafe.authorization.persistence.service.PendingUserAssignmentResolver;
import io.github.isharafe.authorization.spi.AuthorizationAuditPublisher;
import io.github.isharafe.authorization.spi.AuthorizationCacheInvalidator;
import io.github.isharafe.authorization.spi.AuthorizationObservation;
import io.github.isharafe.authorization.spi.ExternalAuthorityMapper;
import io.github.isharafe.authorization.spi.IdentitySynchronizationProvider;
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
@EnableConfigurationProperties(AuthorizationLdapProperties.class)
@ConditionalOnProperty(prefix = "authorization", name = "source", havingValue = "ldap")
@ConditionalOnProperty(
    prefix = "authorization.ldap",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationLdapAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(LdapDirectoryClient.class)
  LdapDirectoryClient ldapDirectoryClient(
      AuthorizationLdapProperties properties, AuthorizationObservation observation) {
    return new JndiLdapDirectoryClient(properties, observation);
  }

  @Bean
  @ConditionalOnMissingBean(IdentitySynchronizationProvider.class)
  LdapIdentitySynchronizationProvider ldapIdentitySynchronizationProvider(
      AuthorizationLdapProperties properties,
      LdapDirectoryClient client,
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
      AuthorizationObservation observation,
      PlatformTransactionManager transactionManager) {
    return new LdapIdentitySynchronizationProvider(
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
        observation,
        transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "authorization.ldap.sync",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  LdapSynchronizationScheduler ldapSynchronizationScheduler(
      IdentitySynchronizationProvider synchronization) {
    return new LdapSynchronizationScheduler(synchronization);
  }
}
