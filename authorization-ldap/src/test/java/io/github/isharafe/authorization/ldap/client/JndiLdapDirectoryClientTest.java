package io.github.isharafe.authorization.ldap.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JndiLdapDirectoryClientTest {
  @Test
  void escapesFilterMetacharactersNullAndUtf8Bytes() {
    assertThat(JndiLdapDirectoryClient.escapeFilterValue("a*)(uid=*)\0café"))
        .isEqualTo("a\\2a\\29\\28uid=\\2a\\29\\00caf\\c3\\a9");
  }
}
