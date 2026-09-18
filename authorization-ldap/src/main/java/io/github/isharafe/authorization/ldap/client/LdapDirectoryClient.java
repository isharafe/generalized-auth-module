package io.github.isharafe.authorization.ldap.client;

import java.util.List;
import java.util.Optional;

public interface LdapDirectoryClient {
  List<LdapUser> users();

  Optional<LdapUser> user(String stableId);
}
