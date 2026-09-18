package io.github.isharafe.authorization.demo;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthorizationDataTransferIntegrationTest {
  private static final String BASE = "/authorization-admin/api";
  private static final String ADMIN = "olivia";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired MockMvc mvc;

  @Test
  void fullSnapshotRestoresTheExportedAuthorizationData() throws Exception {
    mvc.perform(post(BASE + "/data/export")).andExpect(status().isUnauthorized());
    mvc.perform(post(BASE + "/data/export").header("X-Demo-User", "emma"))
        .andExpect(status().isForbidden());

    String snapshot =
        mvc.perform(post(BASE + "/data/export").header("X-Demo-User", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formatVersion", is(1)))
            .andExpect(jsonPath("$.permissions.length()", greaterThan(0)))
            .andExpect(jsonPath("$.roles[*].code", hasItem("AUTHZ_SYSTEM_ADMIN")))
            .andExpect(jsonPath("$.users[*].subject", hasItem(ADMIN)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(
            post(BASE + "/roles")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "TEMPORARY_ROLE",
                      "name": "Temporary role",
                      "enabled": true,
                      "permissionGroups": []
                    }
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            post(BASE + "/data/import")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(snapshot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", greaterThan(0)))
        .andExpect(jsonPath("$.users", greaterThan(0)));

    mvc.perform(get(BASE + "/roles/TEMPORARY_ROLE").header("X-Demo-User", ADMIN))
        .andExpect(status().isNotFound());
    mvc.perform(get(BASE + "/current-user").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject", is(ADMIN)));
  }

  @Test
  void invalidSnapshotIsRejectedBeforeExistingDataIsDeleted() throws Exception {
    String snapshot =
        mvc.perform(post(BASE + "/data/export").header("X-Demo-User", ADMIN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ObjectNode invalid = (ObjectNode) OBJECT_MAPPER.readTree(snapshot);
    JsonNode permissions = invalid.get("permissions");
    ((com.fasterxml.jackson.databind.node.ArrayNode) permissions).removeAll();

    mvc.perform(
            post(BASE + "/data/import")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsBytes(invalid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("AUTHZ_VALIDATION_FAILED")));

    mvc.perform(get(BASE + "/roles/HR_MANAGER").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk());
  }

  @Test
  void unsupportedSnapshotVersionIsRejected() throws Exception {
    ObjectNode unsupported =
        (ObjectNode)
            OBJECT_MAPPER.readTree(
                mvc.perform(post(BASE + "/data/export").header("X-Demo-User", ADMIN))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    unsupported.put("formatVersion", 2);

    mvc.perform(
            post(BASE + "/data/import")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsBytes(unsupported)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("AUTHZ_VALIDATION_FAILED")));

    mvc.perform(get(BASE + "/roles/HR_MANAGER").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is("HR_MANAGER")));
  }
}
