package io.github.isharafe.authorization.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DemoAuthorizationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired DataSource dataSource;

  @Test
  void expectedHttpSemantics() throws Exception {
    mvc.perform(get("/demo/public")).andExpect(status().isOk());
    mvc.perform(get("/demo/profile")).andExpect(status().isUnauthorized());
    mvc.perform(get("/demo/employees")).andExpect(status().isUnauthorized());
    mvc.perform(get("/demo/profile").header("X-Demo-User", "viewer")).andExpect(status().isOk());
    mvc.perform(get("/demo/employees").header("X-Demo-User", "viewer")).andExpect(status().isOk());
    mvc.perform(put("/demo/employees/1").header("X-Demo-User", "viewer"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/demo/employees").header("X-Demo-User", "manager")).andExpect(status().isOk());
    mvc.perform(put("/demo/employees/1").header("X-Demo-User", "manager"))
        .andExpect(status().isOk());
  }

  @Test
  void queryStringDoesNotBypassAuthorization() throws Exception {
    mvc.perform(get("/demo/employees?ignored=/demo/public")).andExpect(status().isUnauthorized());
  }

  @Test
  void contextPathIsNotPartOfTheApplicationOwnedAuthorizationPattern() throws Exception {
    mvc.perform(
            get("/application/demo/public")
                .contextPath("/application")
                .servletPath("/demo/public"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsPathAndMethodBypassVariants() throws Exception {
    mvc.perform(get("/demo/public/")).andExpect(status().isUnauthorized());
    mvc.perform(get(URI.create("/demo//public"))).andExpect(status().isBadRequest());
    mvc.perform(get(URI.create("/demo/public%2F..%2Femployees")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(URI.create("/demo/public%5C..%5Cemployees")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(URI.create("/demo/public;ignored=/employees")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/demo/employees")
                .header("X-Demo-User", "viewer")
                .header("X-HTTP-Method-Override", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void applicationAndAuthorizationFlywayHistoriesAreSeparate() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"authorization_flyway_schema_history\" WHERE \"version\" ="
                    + " '1'",
                Integer.class))
        .isEqualTo(1);
  }
}
