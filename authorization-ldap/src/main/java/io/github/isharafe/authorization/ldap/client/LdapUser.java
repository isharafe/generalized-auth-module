package io.github.isharafe.authorization.ldap.client;

import java.util.Set;

public record LdapUser(
    String id,
    String distinguishedName,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean enabled,
    Set<LdapAuthority> authorities) {
  public LdapUser {
    authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
  }
}
