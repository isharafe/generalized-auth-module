package io.github.isharafe.authorization.nuxtdemo;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.startsWith;
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
    mvc.perform(get("/authorization/ui/permissions")).andExpect(status().isUnauthorized());
  }

  @Test
  void viewerCanReadButCannotEditOrAdminister() throws Exception {
    mvc.perform(get("/authorization/ui/permissions").header("X-Demo-User", "viewer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", containsInAnyOrder("UI:DEMO_PAGE_1")))
        .andExpect(jsonPath("$.permissions", everyItem(startsWith("UI:"))));
    mvc.perform(get("/demo/employees").header("X-Demo-User", "viewer"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/demo/employees/1")
                .header("X-Demo-User", "viewer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viewer update\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCanReadAndEditButCannotAdminister() throws Exception {
    mvc.perform(get("/authorization/ui/permissions").header("X-Demo-User", "manager"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.permissions",
                containsInAnyOrder(
                    "UI:DEMO_PAGE_1", "UI:DEMO_PAGE_2", "UI:EMPLOYEE_EDIT")));
    mvc.perform(
            put("/demo/employees/1")
                .header("X-Demo-User", "manager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ada Byron\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada Byron"));
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "manager"))
        .andExpect(status().isForbidden());
  }

  @Test
  void administratorGetsAdminNavigationAndFrameworkAdministrationAccess() throws Exception {
    mvc.perform(get("/authorization/ui/permissions").header("X-Demo-User", "admin-user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", containsInAnyOrder("UI:AUTHORIZATION_ADMIN")));
    mvc.perform(get("/demo/employees").header("X-Demo-User", "admin-user"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/authorization-admin/").header("X-Demo-User", "admin-user"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/authorization-admin/api/capabilities").header("X-Demo-User", "admin-user"))
        .andExpect(status().isOk());
  }
}
