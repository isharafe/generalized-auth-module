package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import org.springframework.security.core.Authentication;

public interface SpringAuthenticationIdentityResolver {
  AuthenticatedIdentity resolve(Authentication authentication);
}
