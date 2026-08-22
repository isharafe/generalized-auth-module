package com.example.authorization.spi;

import com.example.authorization.domain.AuthenticatedIdentity;
import com.example.authorization.domain.UserEntitlements;

public interface EntitlementProvider {
  UserEntitlements load(AuthenticatedIdentity identity);
}
