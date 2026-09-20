package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.security.UrlSecurityPolicy;
import java.util.Collection;

/** Publishes inspectable method/path policies for an application-owned security filter chain. */
@FunctionalInterface
public interface UrlSecurityPolicyContributor {
  Collection<UrlSecurityPolicy> urlSecurityPolicies();
}
