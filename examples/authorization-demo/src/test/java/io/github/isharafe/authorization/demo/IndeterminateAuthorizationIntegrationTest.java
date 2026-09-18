package io.github.isharafe.authorization.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.isharafe.authorization.spi.ResourceRuleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "authorization.seed.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(IndeterminateAuthorizationIntegrationTest.FailingProviderConfiguration.class)
class IndeterminateAuthorizationIntegrationTest {
  @Autowired MockMvc mvc;

  @Test
  void providerFailureMapsToServiceUnavailable() throws Exception {
    mvc.perform(get("/demo/public").header("X-Demo-User", "manager"))
        .andExpect(status().isServiceUnavailable());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailingProviderConfiguration {
    @Bean
    ResourceRuleProvider failingResourceRuleProvider() {
      return () -> {
        throw new IllegalStateException("database unavailable");
      };
    }
  }
}
