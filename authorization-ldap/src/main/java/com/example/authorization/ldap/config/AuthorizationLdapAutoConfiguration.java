package com.example.authorization.ldap.config;

import com.example.authorization.config.AuthorizationAutoConfiguration;
import com.example.authorization.ldap.client.JndiLdapDirectoryClient;
import com.example.authorization.ldap.client.LdapDirectoryClient;
import com.example.authorization.ldap.sync.LdapIdentitySynchronizationProvider;
import com.example.authorization.ldap.sync.LdapSynchronizationScheduler;
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
  LdapDirectoryClient ldapDirectoryClient(AuthorizationLdapProperties properties) {
    return new JndiLdapDirectoryClient(properties);
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
