package io.github.isharafe.authorization.seed;

import java.util.Collection;

@FunctionalInterface
public interface AuthorizationSeedResourceContributor {
  Collection<AuthorizationSeedResource> seedResources();
}
