package io.github.isharafe.authorization.keycloak.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakGroup(String id, String name, String path) {}
