package com.example.authorization.keycloak.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
    String id,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean enabled,
    Map<String, List<String>> attributes) {}
