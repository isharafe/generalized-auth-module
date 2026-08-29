export type ResourceType = "URL" | "UI";
export type AssignmentSource = "SEED" | "MANUAL" | "IDENTITY_SYNC";

export interface RuntimeConfig {
  apiBasePath: string;
  uiBasePath: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Capabilities {
  source: string;
  identitySynchronization: boolean;
  externalAuthorityMapping: boolean;
  syncProvider: string | null;
}

export interface CurrentUser {
  issuer: string;
  subject: string;
  username?: string | null;
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
}

export interface AuthorizationDataBundle {
  formatVersion: number;
  exportedAt: string;
  permissions: unknown[];
  permissionGroups: unknown[];
  roles: unknown[];
  resourceRules: unknown[];
  users: unknown[];
  externalMappings: unknown[];
  pendingUserAssignments: unknown[];
}

export interface AuthorizationDataImportResult {
  permissions: number;
  permissionGroups: number;
  roles: number;
  resourceRules: number;
  users: number;
  userRoleAssignments: number;
  userPermissionGroupAssignments: number;
  externalMappings: number;
  pendingUserAssignments: number;
}

export interface Role {
  code: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  permissionGroups: string[];
  version: number;
}

export interface PermissionGroup {
  code: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  permissions: string[];
  version: number;
}

export interface Permission {
  code: string;
  name: string;
  description?: string | null;
  resourceType: ResourceType;
  pattern: string;
  enabled: boolean;
  version: number;
}

export interface ResourceRule {
  code: string;
  resourceType: ResourceType;
  pattern: string;
  accessMode: "PERMIT_ALL" | "AUTHENTICATED" | "AUTHORIZED" | "DENY_ALL";
  priority: number;
  enabled: boolean;
  version: number;
}

export interface Assignment {
  code: string;
  source: AssignmentSource;
  sourceReference?: string | null;
}

export interface User {
  id: number;
  issuer: string;
  subject: string;
  username?: string | null;
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  enabled: boolean;
  externalDirectoryId?: string | null;
  lastIdentitySyncAt?: string | null;
  identitySyncStatus?: string | null;
  entitlementVersion: number;
  version: number;
  roles: Assignment[];
  permissionGroups: Assignment[];
}

export interface EffectivePermission {
  code: string;
  name: string;
  description?: string | null;
  resourceType: ResourceType;
  pattern: string;
  enabled: boolean;
}

export interface EffectiveEntitlements {
  identity: { issuer: string; subject: string; username?: string | null };
  roles: string[];
  permissionGroups: string[];
  permissions: EffectivePermission[];
  version: number;
  loadedAt: string;
}

export interface ExternalMapping {
  id?: number;
  sourceSystem: string;
  authorityType: string;
  authorityValue: string;
  targetType: "ROLE" | "PERMISSION_GROUP";
  targetCode: string;
  enabled: boolean;
  version?: number;
}

export interface AuditEvent {
  id: number;
  timestamp: string;
  eventKind: "DECISION" | "CHANGE";
  eventType: string;
  actorIssuer?: string | null;
  actorSubject?: string | null;
  target?: string | null;
  action?: string | null;
  decision?: string | null;
  reason?: string | null;
  ruleCode?: string | null;
  permissionCode?: string | null;
}

export interface AuthorizationTestResponse {
  decision: "GRANTED" | "DENIED" | "INDETERMINATE";
  reason: string;
  matchedRule?: string | null;
  matchedPermission?: string | null;
  assignmentPath: string[];
}

export interface SyncStatus {
  supported: boolean;
  provider?: string | null;
  status: string;
  updatedAt?: string | null;
  details?: string | null;
}
