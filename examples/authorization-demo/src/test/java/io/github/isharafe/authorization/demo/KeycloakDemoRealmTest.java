package io.github.isharafe.authorization.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakDemoRealmTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void browserClientKeepsOnlyRequiredClaimsInAccessTokens() throws IOException {
    JsonNode realm =
        OBJECT_MAPPER.readTree(
            repositoryFile("examples/keycloak-employee-demo/keycloak/employee-demo-realm.json")
                .toFile());
    JsonNode client = findByName(realm.path("clients"), "clientId", "employee-demo");

    assertThat(textValues(client.path("defaultClientScopes"))).containsExactly("basic", "acr");
    assertThat(textValues(client.path("optionalClientScopes"))).containsExactly("offline_access");

    Map<String, JsonNode> mappers = byName(client.path("protocolMappers"));
    assertThat(mappers).containsKeys(
        "preferred-username",
        "full-name",
        "given-name",
        "family-name",
        "email",
        "email-verified",
        "employee-id",
        "manager-dn",
        "department",
        "location",
        "job-title",
        "groups");

    List<String> accessTokenClaims = new ArrayList<>();
    for (JsonNode mapper : mappers.values()) {
      JsonNode config = mapper.path("config");
      if (config.path("access.token.claim").asBoolean()) {
        accessTokenClaims.add(config.path("claim.name").asText());
      }
    }
    assertThat(accessTokenClaims).containsExactly("preferred_username");

    for (String name : List.of(
        "full-name",
        "given-name",
        "family-name",
        "email",
        "email-verified",
        "employee-id",
        "manager-dn",
        "department",
        "location",
        "job-title",
        "groups")) {
      JsonNode config = mappers.get(name).path("config");
      assertThat(config.path("access.token.claim").asBoolean()).as(name).isFalse();
      assertThat(config.path("id.token.claim").asBoolean()).as(name).isTrue();
      assertThat(config.path("userinfo.token.claim").asBoolean()).as(name).isTrue();
    }
  }

  @Test
  void demosRequestOnlyTheOpenIdScope() throws IOException {
    for (String file : List.of(
        "examples/authorization-demo/src/main/resources/application-keycloak-demo.yml",
        "examples/authorization-nuxt-demo/src/main/resources/application-keycloak-demo.yml")) {
      String configuration = Files.readString(repositoryFile(file));
      assertThat(configuration).contains("scope: openid");
      assertThat(configuration).doesNotContain("scope: openid,profile,email");
    }
  }

  private static JsonNode findByName(JsonNode values, String field, String expected) {
    for (JsonNode value : values) {
      if (expected.equals(value.path(field).asText())) return value;
    }
    throw new AssertionError("Missing " + field + "=" + expected);
  }

  private static Map<String, JsonNode> byName(JsonNode values) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (JsonNode value : values) result.put(value.path("name").asText(), value);
    return result;
  }

  private static List<String> textValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) result.add(value.asText());
    return result;
  }

  private static Path repositoryFile(String relativePath) {
    Path directory = Path.of("").toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve(relativePath);
      if (Files.isRegularFile(candidate)) return candidate;
      directory = directory.getParent();
    }
    throw new AssertionError("Cannot locate repository file " + relativePath);
  }
}
