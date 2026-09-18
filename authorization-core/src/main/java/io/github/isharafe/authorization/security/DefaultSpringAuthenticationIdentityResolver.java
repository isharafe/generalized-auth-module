package io.github.isharafe.authorization.security;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

public final class DefaultSpringAuthenticationIdentityResolver
    implements SpringAuthenticationIdentityResolver {
  @Override
  public AuthenticatedIdentity resolve(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) return null;
    Object principal = authentication.getPrincipal();
    if (principal instanceof OAuth2AuthenticatedPrincipal oauth) {
      Map<String, Object> attributes = oauth.getAttributes();
      String issuer = String.valueOf(attributes.getOrDefault("iss", "local"));
      String subject = String.valueOf(attributes.getOrDefault("sub", authentication.getName()));
      return new AuthenticatedIdentity(issuer, subject, authentication.getName());
    }
    return new AuthenticatedIdentity("local", authentication.getName(), authentication.getName());
  }
}
