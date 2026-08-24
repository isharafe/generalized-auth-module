package com.example.authorization.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUiIntegrationTest {
  private static final String BASE = "/authorization-admin";
  private static final String ADMIN = "manager";

  @Autowired MockMvc mvc;

  @Test
  void adminUiIsProtectedByFrameworkPermissions() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE).header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE).header("X-Demo-User", ADMIN))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(BASE + "/"));
  }

  @Test
  void demoBrowserSessionCanBootstrapManagerAuthentication() throws Exception {
    Cookie demoCookie =
        mvc.perform(get(BASE).param("demo-user", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(cookie().exists(DemoHeaderAuthenticationFilter.DEMO_USER_COOKIE))
            .andReturn()
            .getResponse()
            .getCookie(DemoHeaderAuthenticationFilter.DEMO_USER_COOKIE);

    mvc.perform(get(BASE + "/").cookie(demoCookie))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Authorization Console</title>")));
  }

  @Test
  void authorizedAdminCanLoadThePackagedUiAndRuntimeConfiguration() throws Exception {
    mvc.perform(get(BASE + "/").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Authorization Console</title>")))
        .andExpect(content().string(containsString("./assets/index-")));

    mvc.perform(get(BASE + "/config").header("X-Demo-User", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiBasePath", is("/authorization-admin/api")))
        .andExpect(jsonPath("$.uiBasePath", is(BASE)));
  }
}
