package io.github.isharafe.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceTypeTest {
  @Test
  void exposesTheSupportedResourceTypes() {
    assertThat(ResourceType.values()).containsExactly(ResourceType.URL, ResourceType.UI);
  }

  @Test
  void genericDomainPatternsRemainOpaque() {
    Permission permission =
        new Permission("UI:UI_VIEW", "View UI", null, ResourceType.UI, "employees.edit", true);
    ProtectedResource resource = new ProtectedResource(ResourceType.UI, "employees.edit");

    assertThat(permission.pattern()).isEqualTo("employees.edit");
    assertThat(resource.pattern()).isEqualTo("employees.edit");
  }
}
