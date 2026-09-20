package io.github.isharafe.authorization.nuxtdemo;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthorizationNuxtDemoIntegrationTest {
  @Autowired private MockMvc mvc;

  @Test
  void anonymousRequestsFollowTheConfiguredAccessModes() throws Exception {
    mvc.perform(get("/demo/public")).andExpect(status().isOk());
    mvc.perform(get("/demo/profile")).andExpect(status().isUnauthorized());
    mvc.perform(get("/demo/employees")).andExpect(status().isUnauthorized());
    mvc.perform(get("/authorization/user/permissions")).andExpect(status().isUnauthorized());
  }

  @Test
  void hrAnalystCanReadButCannotEditOrAdminister() throws Exception {
    mvc.perform(get("/authorization/user/permissions").header("X-Demo-User", "emma"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.permissions",
                containsInAnyOrder("UI:EMPLOYEE_DIRECTORY", "URL:EMPLOYEE_VIEW")));
    mvc.perform(get("/demo/employees").header("X-Demo-User", "emma"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/demo/employees/1")
                .header("X-Demo-User", "emma")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Analyst update\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "emma"))
        .andExpect(status().isForbidden());
  }

  @Test
  void hrManagerCanReadAndEditButCannotAdminister() throws Exception {
    mvc.perform(get("/authorization/user/permissions").header("X-Demo-User", "michael"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.permissions",
                containsInAnyOrder(
                    "UI:EMPLOYEE_DIRECTORY",
                    "UI:MANAGER_WORKSPACE",
                    "UI:EMPLOYEE_EDIT",
                    "URL:EMPLOYEE_VIEW",
                    "URL:EMPLOYEE_EDIT")));
    mvc.perform(
            put("/demo/employees/1")
                .header("X-Demo-User", "michael")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ada Byron\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada Byron"));
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "michael"))
        .andExpect(status().isForbidden());
  }

  @Test
  void administratorGetsAdminNavigationAndFrameworkAdministrationAccess() throws Exception {
    mvc.perform(
            get("/authorization/user/permissions")
                .header("X-Demo-User", "olivia"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.permissions",
                hasItems("UI:AUTHORIZATION_ADMIN", "URL:AUTHZ_ADMIN_UI")));
    mvc.perform(get("/demo/employees").header("X-Demo-User", "olivia"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "olivia"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/authorization-admin/api/capabilities")
                .header("X-Demo-User", "olivia"))
        .andExpect(status().isOk());
  }
}
