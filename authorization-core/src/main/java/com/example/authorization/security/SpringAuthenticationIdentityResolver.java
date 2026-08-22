package com.example.authorization.security;

import com.example.authorization.domain.AuthenticatedIdentity;
import org.springframework.security.core.Authentication;

public interface SpringAuthenticationIdentityResolver {
  AuthenticatedIdentity resolve(Authentication authentication);
}
