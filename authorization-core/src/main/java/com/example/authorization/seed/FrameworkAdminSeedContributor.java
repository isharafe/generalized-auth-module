package com.example.authorization.seed;

import com.example.authorization.domain.ResourceType;
import java.util.Arrays;

public final class FrameworkAdminSeedContributor implements AuthorizationSeedContributor {
  private static final String DEFAULT_BASE_PATH = "/authorization-admin/api";
  private static final String DEFAULT_UI_BASE_PATH = "/authorization-admin";
  private static final String[][] PERMISSIONS = {
    {
      "AUTHZ_ADMIN_VIEW",
      "View authorization administration",
      "GET:/authorization-admin/api/capabilities"
    },
    {
      "AUTHZ_ADMIN_UI",
      "Use authorization administration UI",
      "GET:/authorization-admin/**"
    },
    {"AUTHZ_USER_VIEW", "View users", "GET:/authorization-admin/api/users/**"},
    {"AUTHZ_USER_MANAGE", "Manage users", "*:/authorization-admin/api/users/**"},
    {"AUTHZ_ROLE_VIEW", "View roles", "GET:/authorization-admin/api/roles/**"},
    {"AUTHZ_ROLE_MANAGE", "Manage roles", "*:/authorization-admin/api/roles/**"},
    {
      "AUTHZ_PERMISSION_VIEW",
      "View permissions",
      "GET:/authorization-admin/api/permissions/**"
    },
    {
      "AUTHZ_PERMISSION_MANAGE",
      "Manage permissions",
      "*:/authorization-admin/api/permissions/**"
    },
    {
      "AUTHZ_PERMISSION_GROUP_VIEW",
      "View permission groups",
      "GET:/authorization-admin/api/permission-groups/**"
    },
    {
      "AUTHZ_PERMISSION_GROUP_MANAGE",
      "Manage permission groups",
      "*:/authorization-admin/api/permission-groups/**"
    },
    {
      "AUTHZ_RESOURCE_RULE_VIEW",
      "View resource rules",
      "GET:/authorization-admin/api/resource-rules/**"
    },
    {
      "AUTHZ_RESOURCE_RULE_MANAGE",
      "Manage resource rules",
      "*:/authorization-admin/api/resource-rules/**"
    },
    {
      "AUTHZ_EXTERNAL_MAPPING_VIEW",
      "View external mappings",
      "GET:/authorization-admin/api/external-mappings/**"
    },
    {
      "AUTHZ_EXTERNAL_MAPPING_MANAGE",
      "Manage external mappings",
      "*:/authorization-admin/api/external-mappings/**"
    },
    {"AUTHZ_SYNC_VIEW", "View synchronization", "GET:/authorization-admin/api/sync/**"},
    {"AUTHZ_SYNC_RUN", "Run synchronization", "POST:/authorization-admin/api/sync/**"},
    {"AUTHZ_AUDIT_VIEW", "View audit", "GET:/authorization-admin/api/audit"},
    {
      "AUTHZ_AUTHORIZATION_TEST",
      "Test authorization decisions",
      "POST:/authorization-admin/api/authorization-test"
    }
  };

  private final String basePath;
  private final String uiBasePath;

  public FrameworkAdminSeedContributor() {
    this(DEFAULT_BASE_PATH, DEFAULT_UI_BASE_PATH);
  }

  public FrameworkAdminSeedContributor(String basePath) {
    this(basePath, DEFAULT_UI_BASE_PATH);
  }

  public FrameworkAdminSeedContributor(String basePath, String uiBasePath) {
    this.basePath = normalized(basePath, "Admin API");
    this.uiBasePath = normalized(uiBasePath, "Admin UI");
  }

  private String normalized(String value, String name) {
    if (value == null || value.isBlank() || !value.startsWith("/"))
      throw new IllegalArgumentException(name + " base path must start with /");
    return value.length() > 1 && value.endsWith("/")
        ? value.substring(0, value.length() - 1)
        : value;
  }

  @Override
  public void contribute(AuthorizationSeedBuilder seed) {
    for (String[] permission : PERMISSIONS)
      seed.permission(
          permission[0],
          permission[1],
          ResourceType.URL,
          "AUTHZ_ADMIN_UI".equals(permission[0])
              ? permission[2].replace(DEFAULT_UI_BASE_PATH, uiBasePath)
              : permission[2].replace(DEFAULT_BASE_PATH, basePath));
    String[] codes = Arrays.stream(PERMISSIONS).map(value -> value[0]).toArray(String[]::new);
    seed.permissionGroup(
        "AUTHZ_SYSTEM_VIEWER",
        "Authorization system viewer",
        "AUTHZ_ADMIN_VIEW",
        "AUTHZ_ADMIN_UI",
        "AUTHZ_USER_VIEW",
        "AUTHZ_ROLE_VIEW",
        "AUTHZ_PERMISSION_VIEW",
        "AUTHZ_PERMISSION_GROUP_VIEW",
        "AUTHZ_RESOURCE_RULE_VIEW",
        "AUTHZ_EXTERNAL_MAPPING_VIEW",
        "AUTHZ_SYNC_VIEW",
        "AUTHZ_AUDIT_VIEW",
        "AUTHZ_AUTHORIZATION_TEST");
    seed.permissionGroup("AUTHZ_SYSTEM_ADMIN", "Authorization system administrator", codes);
    seed.role("AUTHZ_SYSTEM_VIEWER", "Authorization system viewer", "AUTHZ_SYSTEM_VIEWER");
    seed.role("AUTHZ_SYSTEM_ADMIN", "Authorization system administrator", "AUTHZ_SYSTEM_ADMIN");
  }
}
