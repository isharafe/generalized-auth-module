package io.github.isharafe.authorization.keycloak.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {}
