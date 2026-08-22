package com.example.authorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authorization.domain.AccessMode;
import com.example.authorization.domain.Permission;
import com.example.authorization.domain.ProtectedResource;
import com.example.authorization.domain.ResourceRule;
import com.example.authorization.domain.ResourceType;
import com.example.authorization.spi.ResourcePatternMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPermissionMatcherTest {
  private final DefaultPermissionMatcher matcher = new DefaultPermissionMatcher();

  @Test
  void singleWildcardMatchesOneSegmentOnly() {
    Permission permission = permission("/api/employees/*");
    assertThat(matcher.matches(permission, resource("/api/employees/1"))).isTrue();
    assertThat(matcher.matches(permission, resource("/api/employees/1/details"))).isFalse();
  }

  @Test
  void doubleWildcardMatchesDescendants() {
    assertThat(
            matcher.matches(permission("/api/employees/**"), resource("/api/employees/1/details")))
        .isTrue();
  }

  @Test
  void methodMustMatchUnlessWildcarded() {
    Permission get = permission("/api/**");
    assertThat(matcher.matches(get, new ProtectedResource(ResourceType.URL, "PUT:/api/1"))).isFalse();
  }

  @Test
  void resourceTypeMustMatch() {
    Permission uiPermission =
        new Permission("UI_VIEW", "View UI", null, ResourceType.UI, "employees.edit", true);
    assertThat(matcher.matches(uiPermission, resource("/screen"))).isFalse();
  }

  @Test
  void uiPermissionMatchesTheSameOpaqueIdentifier() {
    Permission permission =
        new Permission("UI_VIEW", "View UI", null, ResourceType.UI, "employees.edit", true);

    assertThat(
            matcher.matches(
                permission, new ProtectedResource(ResourceType.UI, "employees.edit")))
        .isTrue();
  }

  @Test
  void uiResourceRuleMatchesTheSameOpaqueIdentifier() {
    ResourceRule rule =
        new ResourceRule(
            "UI_EMPLOYEES", ResourceType.UI, "employees.edit", AccessMode.AUTHORIZED, 0, true);

    assertThat(
            matcher.matches(rule, new ProtectedResource(ResourceType.UI, "employees.edit")))
        .isTrue();
    assertThat(
            matcher.matches(rule, new ProtectedResource(ResourceType.UI, "employees.view")))
        .isFalse();
  }

  @Test
  void registeredStrategyOverridesTheBuiltInStrategyForItsType() {
    ResourcePatternMatcher uiOverride =
        new ResourcePatternMatcher() {
          @Override
          public ResourceType resourceType() {
            return ResourceType.UI;
          }

          @Override
          public boolean matches(String expectedPattern, String actualPattern) {
            return false;
          }

          @Override
          public int compareSpecificity(String leftPattern, String rightPattern) {
            return 0;
          }
        };
    DefaultPermissionMatcher registry = new DefaultPermissionMatcher(List.of(uiOverride));
    Permission permission =
        new Permission("UI_VIEW", "View UI", null, ResourceType.UI, "employees.edit", true);

    assertThat(
            registry.matches(
                permission, new ProtectedResource(ResourceType.UI, "employees.edit")))
        .isFalse();
  }

  @Test
  void rejectsMultipleOverridesForTheSameResourceType() {
    ResourcePatternMatcher first = uiStrategy();
    ResourcePatternMatcher second = uiStrategy();

    assertThatThrownBy(() -> new DefaultPermissionMatcher(List.of(first, second)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Multiple resource pattern matchers for UI");
  }

  @Test
  void colonInPathIsPreserved() {
    assertThat(matcher.matches(permission("/api/items/{id}"), resource("/api/items/a:b"))).isTrue();
  }

  @Test
  void trailingAndRepeatedSlashesAreNotSilentlyNormalized() {
    Permission exact = permission("/api/employees");
    assertThat(matcher.matches(exact, resource("/api/employees/"))).isFalse();
    assertThat(matcher.matches(exact, resource("/api//employees"))).isFalse();
  }

  private ResourcePatternMatcher uiStrategy() {
    return new ResourcePatternMatcher() {
      @Override
      public ResourceType resourceType() {
        return ResourceType.UI;
      }

      @Override
      public boolean matches(String expectedPattern, String actualPattern) {
        return expectedPattern.equals(actualPattern);
      }

      @Override
      public int compareSpecificity(String leftPattern, String rightPattern) {
        return 0;
      }
    };
  }

  private Permission permission(String path) {
    return new Permission("TEST", "Test", null, ResourceType.URL, "GET:" + path, true);
  }

  private ProtectedResource resource(String path) {
    return new ProtectedResource(ResourceType.URL, "GET:" + path);
  }
}
