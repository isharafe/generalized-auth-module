package com.example.authorization.demo;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiIntegrationTest {
  private static final String BASE = "/authorization-admin/api";
  private static final String ADMIN = "manager";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void adminApiIsProtectedAndCapabilitiesAreProviderAware() throws Exception {
    mvc.perform(get(BASE + "/capabilities")).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE + "/capabilities").header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/capabilities").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source", is("DATABASE")))
        .andExpect(jsonPath("$.identitySynchronization", is(false)))
        .andExpect(jsonPath("$.externalAuthorityMapping", is(true)));

    mvc.perform(get(BASE + "/current-user").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer", is("local")))
        .andExpect(jsonPath("$.subject", is(ADMIN)))
        .andExpect(jsonPath("$.username", is(ADMIN)));
  }

  @Test
  void roleCrudSupportsSearchVersionConflictsSoftDisableAndAudit() throws Exception {
    mvc.perform(
            post(BASE + "/roles")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_TEST_ROLE",
                      "name": "Phase 2 test role",
                      "description": "created through the admin API",
                      "enabled": true,
                      "permissionGroups": []
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code", is("PHASE2_TEST_ROLE")))
        .andExpect(jsonPath("$.version", is(0)));

    mvc.perform(
            get(BASE + "/roles")
                .header("X-Demo-User", ADMIN)
                .param("search", "phase2_test")
                .param("page", "0")
                .param("size", "5")
                .param("sort", "code,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].code", is("PHASE2_TEST_ROLE")));

    mvc.perform(
            put(BASE + "/roles/PHASE2_TEST_ROLE")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_TEST_ROLE",
                      "name": "Updated role",
                      "enabled": true,
                      "permissionGroups": [],
                      "version": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            put(BASE + "/roles/PHASE2_TEST_ROLE")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_TEST_ROLE",
                      "name": "Stale update",
                      "enabled": true,
                      "permissionGroups": [],
                      "version": 0
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", is("AUTHZ_CONCURRENT_MODIFICATION")));

    mvc.perform(
            delete(BASE + "/roles/PHASE2_TEST_ROLE")
                .header("X-Demo-User", ADMIN)
                .param("version", "1"))
        .andExpect(status().isNoContent());

    mvc.perform(
            get(BASE + "/roles/PHASE2_TEST_ROLE").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled", is(false)));

    mvc.perform(
            get(BASE + "/audit")
                .header("X-Demo-User", ADMIN)
                .param("eventKind", "CHANGE")
                .param("eventType", "ADMIN_CREATE")
                .param("target", "PHASE2_TEST_ROLE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].eventKind", is("CHANGE")))
        .andExpect(jsonPath("$.content[0].target", is("ROLE:PHASE2_TEST_ROLE")))
        .andExpect(jsonPath("$.content[0].actorSubject", is("manager")));
  }

  @Test
  void permissionGroupRuleAndExternalMappingCrudValidateReferencesAndPatterns()
      throws Exception {
    mvc.perform(
            post(BASE + "/permissions")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_UI_VIEW",
                      "name": "View Phase 2 UI",
                      "resourceType": "UI",
                      "pattern": "phase2.dashboard",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            post(BASE + "/permission-groups")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_UI_GROUP",
                      "name": "Phase 2 UI group",
                      "enabled": true,
                      "permissions": ["PHASE2_UI_VIEW"]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.permissions", hasItem("PHASE2_UI_VIEW")));

    mvc.perform(
            post(BASE + "/permissions")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_BAD_URL",
                      "name": "Bad URL",
                      "resourceType": "URL",
                      "pattern": "get:not-a-path",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("AUTHZ_VALIDATION_FAILED")));

    mvc.perform(
            post(BASE + "/resource-rules")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CONFLICT",
                      "resourceType": "URL",
                      "pattern": "*:/demo/public",
                      "accessMode": "DENY_ALL",
                      "priority": 0,
                      "enabled": true
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", is("AUTHZ_RESOURCE_RULE_CONFLICT")));

    mvc.perform(
            post(BASE + "/external-mappings")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceSystem": "TEST",
                      "authorityType": "GROUP",
                      "authorityValue": "/phase2/testers",
                      "targetType": "ROLE",
                      "targetCode": "PHASE2_MISSING_ROLE",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code", is("AUTHZ_ROLE_NOT_FOUND")));

    mvc.perform(
            post(BASE + "/external-mappings")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceSystem": "TEST",
                      "authorityType": "GROUP",
                      "authorityValue": "/phase2/testers",
                      "targetType": "PERMISSION_GROUP",
                      "targetCode": "PHASE2_UI_GROUP",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetCode", is("PHASE2_UI_GROUP")));
  }


  @Test
  void allConfigurationCrudAndMappingEndpointsAreUsable() throws Exception {
    mvc.perform(
            post(BASE + "/permissions")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_PERMISSION",
                      "name": "CRUD permission",
                      "resourceType": "UI",
                      "pattern": "phase2.crud",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is(0)));

    mvc.perform(
            put(BASE + "/permissions/PHASE2_CRUD_PERMISSION")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_PERMISSION",
                      "name": "Updated CRUD permission",
                      "resourceType": "UI",
                      "pattern": "phase2.crud",
                      "enabled": true,
                      "version": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            post(BASE + "/permission-groups")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_GROUP",
                      "name": "CRUD group",
                      "enabled": true,
                      "permissions": []
                    }
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            put(BASE + "/permission-groups/PHASE2_CRUD_GROUP/permissions/PHASE2_CRUD_PERMISSION")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", hasItem("PHASE2_CRUD_PERMISSION")))
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            delete(BASE + "/permission-groups/PHASE2_CRUD_GROUP/permissions/PHASE2_CRUD_PERMISSION")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", hasSize(0)))
        .andExpect(jsonPath("$.version", is(2)));

    mvc.perform(
            put(BASE + "/permission-groups/PHASE2_CRUD_GROUP")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_GROUP",
                      "name": "Updated CRUD group",
                      "enabled": true,
                      "permissions": ["PHASE2_CRUD_PERMISSION"],
                      "version": 2
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(3)));

    mvc.perform(
            post(BASE + "/roles")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_ROLE",
                      "name": "CRUD role",
                      "enabled": true,
                      "permissionGroups": []
                    }
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            put(BASE + "/roles/PHASE2_CRUD_ROLE/permission-groups/PHASE2_CRUD_GROUP")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissionGroups", hasItem("PHASE2_CRUD_GROUP")))
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            delete(BASE + "/roles/PHASE2_CRUD_ROLE/permission-groups/PHASE2_CRUD_GROUP")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissionGroups", hasSize(0)))
        .andExpect(jsonPath("$.version", is(2)));

    mvc.perform(
            post(BASE + "/resource-rules")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_RULE",
                      "resourceType": "UI",
                      "pattern": "phase2.crud",
                      "accessMode": "AUTHORIZED",
                      "priority": 1,
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is(0)));

    mvc.perform(
            put(BASE + "/resource-rules/PHASE2_CRUD_RULE")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_CRUD_RULE",
                      "resourceType": "UI",
                      "pattern": "phase2.crud",
                      "accessMode": "DENY_ALL",
                      "priority": 2,
                      "enabled": true,
                      "version": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            post(BASE + "/external-mappings")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceSystem": "PHASE2",
                      "authorityType": "GROUP",
                      "authorityValue": "/crud",
                      "targetType": "ROLE",
                      "targetCode": "PHASE2_CRUD_ROLE",
                      "enabled": true
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is(0)));

    Long mappingId =
        jdbc.queryForObject(
            "select ID from AUTH_EXTERNAL_AUTHORITY_MAPPING where SOURCE_SYSTEM = 'PHASE2'",
            Long.class);

    mvc.perform(
            put(BASE + "/external-mappings/" + mappingId)
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceSystem": "PHASE2",
                      "authorityType": "GROUP",
                      "authorityValue": "/crud-updated",
                      "targetType": "ROLE",
                      "targetCode": "PHASE2_CRUD_ROLE",
                      "enabled": true,
                      "version": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(1)));

    mvc.perform(
            delete(BASE + "/external-mappings/" + mappingId)
                .header("X-Demo-User", ADMIN)
                .param("version", "1"))
        .andExpect(status().isNoContent());

    mvc.perform(
            delete(BASE + "/resource-rules/PHASE2_CRUD_RULE")
                .header("X-Demo-User", ADMIN)
                .param("version", "1"))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete(BASE + "/roles/PHASE2_CRUD_ROLE")
                .header("X-Demo-User", ADMIN)
                .param("version", "2"))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete(BASE + "/permission-groups/PHASE2_CRUD_GROUP")
                .header("X-Demo-User", ADMIN)
                .param("version", "3"))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete(BASE + "/permissions/PHASE2_CRUD_PERMISSION")
                .header("X-Demo-User", ADMIN)
                .param("version", "1"))
        .andExpect(status().isNoContent());
  }

  @Test
  void userAssignmentsEffectivePermissionsAndExplainAreFunctional() throws Exception {
    Long viewerId =
        jdbc.queryForObject(
            "select ID from AUTH_USER where EXTERNAL_ISSUER = 'local' and EXTERNAL_SUBJECT = 'viewer'",
            Long.class);



    mvc.perform(
            post(BASE + "/roles")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PHASE2_USER_ROLE",
                      "name": "User assignment role",
                      "enabled": true,
                      "permissionGroups": []
                    }
                    """))
        .andExpect(status().isCreated());

    mvc.perform(
            put(BASE + "/users/" + viewerId + "/roles/PHASE2_USER_ROLE")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.roles[?(@.code == 'PHASE2_USER_ROLE')].source",
                hasItem("MANUAL")));

    mvc.perform(
            get(BASE + "/roles/PHASE2_USER_ROLE/users")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(viewerId.intValue())));

    mvc.perform(
            delete(BASE + "/users/" + viewerId + "/roles/PHASE2_USER_ROLE")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk());

    mvc.perform(
            delete(BASE + "/roles/PHASE2_USER_ROLE")
                .header("X-Demo-User", ADMIN)
                .param("version", "0"))
        .andExpect(status().isNoContent());
    mvc.perform(
            put("/demo/employees/123").header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());

    mvc.perform(
            put(BASE + "/users/" + viewerId + "/permission-groups/EMPLOYEE_MANAGER")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.permissionGroups[?(@.code == 'EMPLOYEE_MANAGER')].source",
                hasItem("MANUAL")));

    mvc.perform(
            put("/demo/employees/123").header("X-Demo-User", "viewer"))
        .andExpect(status().isOk());

    mvc.perform(
            get(BASE + "/users/" + viewerId + "/effective-permissions")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions[*].code", hasItem("EMPLOYEE_EDIT")));

    mvc.perform(
            delete(BASE + "/users/" + viewerId + "/roles/HR_VIEWER")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", is("AUTHZ_ASSIGNMENT_SOURCE_CONFLICT")));

    mvc.perform(
            post(BASE + "/authorization-test")
                .header("X-Demo-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "identity": {
                        "issuer": "local",
                        "subject": "manager",
                        "username": "manager"
                      },
                      "resourceType": "URL",
                      "method": "PUT",
                      "path": "/demo/employees/123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision", is("GRANTED")))
        .andExpect(jsonPath("$.matchedPermission", is("EMPLOYEE_EDIT")))
        .andExpect(jsonPath("$.assignmentPath", hasItem("ROLE:HR_MANAGER")));

    mvc.perform(
            delete(BASE + "/users/" + viewerId + "/permission-groups/EMPLOYEE_MANAGER")
                .header("X-Demo-User", ADMIN))
        .andExpect(status().isOk());

    mvc.perform(
            put("/demo/employees/123").header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());
  }

  @Test
  void databaseModeExposesSyncFacadeAsUnsupported() throws Exception {
    mvc.perform(get(BASE + "/sync/status").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supported", is(false)))
        .andExpect(jsonPath("$.status", is("UNSUPPORTED")));

    mvc.perform(post(BASE + "/sync/full").header("X-Demo-User", ADMIN))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.code", is("AUTHZ_SYNC_UNSUPPORTED")));
  }
}
