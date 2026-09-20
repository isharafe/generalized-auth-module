package io.github.isharafe.authorization.admin.seed;

import io.github.isharafe.authorization.domain.PermissionCode;
import io.github.isharafe.authorization.domain.ResourceType;
import io.github.isharafe.authorization.seed.AuthorizationSeedBuilder;
import io.github.isharafe.authorization.seed.AuthorizationSeedContributor;
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
      "AUTHZ_ADMIN_CURRENT_USER",
      "View the current authorization administrator",
      "GET:/authorization-admin/api/current-user"
    },
    {
      "AUTHZ_ADMIN_UI",
      "Use authorization administration UI",
      "GET:/authorization-admin"
    },
    {
      "AUTHZ_ADMIN_UI_INDEX",
      "Load authorization administration UI",
      "GET:/authorization-admin/"
    },
    {
      "AUTHZ_ADMIN_UI_CONFIG",
      "Load authorization administration UI configuration",
      "GET:/authorization-admin/config"
    },
    {
      "AUTHZ_ADMIN_UI_ASSETS",
      "Load authorization administration UI assets",
      "GET:/authorization-admin/assets/**"
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
      "AUTHZ_RESOURCE_INVENTORY_VIEW",
      "View available URL resources and rule coverage",
      "GET:/authorization-admin/api/resource-inventory/urls"
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
    },
    {
      "AUTHZ_DATA_EXPORT",
      "Export authorization data",
      "POST:/authorization-admin/api/data/export"
    },
    {
      "AUTHZ_DATA_IMPORT",
      "Replace authorization data from an export",
      "POST:/authorization-admin/api/data/import"
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
          url(permission[0]),
          permission[1],
          ResourceType.URL,
          permission[0].startsWith("AUTHZ_ADMIN_UI")
              ? permission[2].replace(DEFAULT_UI_BASE_PATH, uiBasePath)
              : permission[2].replace(DEFAULT_BASE_PATH, basePath));
    String[] codes = Arrays.stream(PERMISSIONS).map(value -> url(value[0])).toArray(String[]::new);
    seed.permissionGroup(
        "AUTHZ_SYSTEM_VIEWER",
        "Authorization system viewer",
        url("AUTHZ_ADMIN_VIEW"),
        url("AUTHZ_ADMIN_CURRENT_USER"),
        url("AUTHZ_ADMIN_UI"),
        url("AUTHZ_ADMIN_UI_INDEX"),
        url("AUTHZ_ADMIN_UI_CONFIG"),
        url("AUTHZ_ADMIN_UI_ASSETS"),
        url("AUTHZ_USER_VIEW"),
        url("AUTHZ_ROLE_VIEW"),
        url("AUTHZ_PERMISSION_VIEW"),
        url("AUTHZ_PERMISSION_GROUP_VIEW"),
        url("AUTHZ_RESOURCE_RULE_VIEW"),
        url("AUTHZ_RESOURCE_INVENTORY_VIEW"),
        url("AUTHZ_EXTERNAL_MAPPING_VIEW"),
        url("AUTHZ_SYNC_VIEW"),
        url("AUTHZ_AUDIT_VIEW"),
        url("AUTHZ_AUTHORIZATION_TEST"));
    seed.permissionGroup("AUTHZ_SYSTEM_ADMIN", "Authorization system administrator", codes);
    seed.role("AUTHZ_SYSTEM_VIEWER", "Authorization system viewer", "AUTHZ_SYSTEM_VIEWER");
    seed.role("AUTHZ_SYSTEM_ADMIN", "Authorization system administrator", "AUTHZ_SYSTEM_ADMIN");
  }

  private String url(String localCode) {
    return PermissionCode.of(ResourceType.URL, localCode);
  }
}
