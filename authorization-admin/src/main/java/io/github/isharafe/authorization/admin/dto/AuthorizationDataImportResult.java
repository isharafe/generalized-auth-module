package io.github.isharafe.authorization.admin.dto;

public record AuthorizationDataImportResult(
    int permissions,
    int permissionGroups,
    int roles,
    int resourceRules,
    int users,
    int userRoleAssignments,
    int userPermissionGroupAssignments,
    int externalMappings,
    int pendingUserAssignments) {}
