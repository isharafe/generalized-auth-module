import type { PermissionMatch, PermissionRequirement } from "./types";

export function normalizeRequirement(
  value: string | string[] | PermissionRequirement
): { permissions: string[]; match: PermissionMatch } {
  if (typeof value === "string") return { permissions: [value], match: "all" };
  if (Array.isArray(value)) return { permissions: value, match: "all" };
  return {
    permissions: typeof value.permissions === "string" ? [value.permissions] : value.permissions,
    match: value.match ?? "all"
  };
}

export function matchesPermissions(
  granted: ReadonlySet<string>,
  requirement: string | string[] | PermissionRequirement
): boolean {
  const normalized = normalizeRequirement(requirement);
  if (normalized.permissions.length === 0) return false;
  const permissions = normalized.permissions.map(validatePermissionCode);
  return normalized.match === "any"
    ? permissions.some((permission) => granted.has(permission))
    : permissions.every((permission) => granted.has(permission));
}

export function validatePermissionCode(permission: string): string {
  if (
    permission.length > 100
    || !/^[A-Z][A-Z0-9_]*:[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(permission)
  ) {
    throw new Error(
      `Permission codes must use the canonical TYPE:<code> form and contain at most 100 characters: ${permission}`
    );
  }
  return permission;
}
