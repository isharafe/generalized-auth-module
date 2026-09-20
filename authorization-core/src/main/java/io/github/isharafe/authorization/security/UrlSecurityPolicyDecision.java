package io.github.isharafe.authorization.security;

/** Describes how a security-filter-chain matcher handles a matching URL. */
public enum UrlSecurityPolicyDecision {
  PERMIT_ALL,
  AUTHENTICATED,
  AUTHORIZED,
  DENY_ALL,
  RESOURCE_RULES
}
