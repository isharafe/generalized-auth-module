package io.github.isharafe.authorization.spi;

import io.github.isharafe.authorization.domain.AuthenticatedIdentity;
import io.github.isharafe.authorization.domain.UserEntitlements;

public interface EntitlementProvider {
  UserEntitlements load(AuthenticatedIdentity identity);
}
