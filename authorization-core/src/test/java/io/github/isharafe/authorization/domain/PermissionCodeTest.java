package io.github.isharafe.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PermissionCodeTest {
  @Test
  void createsCanonicalCode() {
    String code = PermissionCode.of(ResourceType.URL, "EMPLOYEE_VIEW");

    assertThat(code).isEqualTo("URL:EMPLOYEE_VIEW");
  }

  @Test
  void permitsTheSameLocalCodeForDifferentResourceTypes() {
    assertThat(PermissionCode.of(ResourceType.URL, "VIEW"))
        .isNotEqualTo(PermissionCode.of(ResourceType.UI, "VIEW"));
  }

  @Test
  void rejectsMissingOrMismatchedTypePrefix() {
    assertThatThrownBy(() -> PermissionCode.validate("EMPLOYEE_VIEW", ResourceType.URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must start with URL:");
    assertThatThrownBy(() -> PermissionCode.validate("UI:EMPLOYEE_VIEW", ResourceType.URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must start with URL:");
  }

  @Test
  void rejectsInvalidLocalCodeAndOverlongCanonicalCode() {
    assertThatThrownBy(() -> PermissionCode.of(ResourceType.UI, "bad code"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("local code");
    assertThatThrownBy(() -> PermissionCode.of(ResourceType.URL, "A".repeat(97)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most 100");
  }
}
