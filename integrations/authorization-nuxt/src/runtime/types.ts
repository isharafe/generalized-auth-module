import type { ComputedRef, Ref } from "vue";
import type { FetchOptions } from "ofetch";

export type AuthorizationStatus =
  | "idle"
  | "loading"
  | "ready"
  | "unauthenticated"
  | "error";

export type PermissionMatch = "all" | "any";

export interface PermissionRequirement {
  permissions: string | string[];
  match?: PermissionMatch;
}

export interface PermissionsResponse {
  permissions: string[];
  entitlementVersion: number;
}

export interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface AuthorizationState {
  permissions: string[];
  entitlementVersion: number;
  status: AuthorizationStatus;
  error: string | null;
}

export interface AuthorizationPublicConfig {
  apiProxyPrefix: string;
  permissionsEndpoint: string;
  csrfEndpoint: string;
  refreshEndpoint: string;
  logoutEndpoint: string;
  loginEndpoint: string;
  deniedRedirect: string;
}

export interface AuthorizationManager {
  permissions: ComputedRef<readonly string[]>;
  entitlementVersion: ComputedRef<number>;
  status: ComputedRef<AuthorizationStatus>;
  error: ComputedRef<string | null>;
  can(permission: string): boolean;
  canAll(permissions: readonly string[]): boolean;
  canAny(permissions: readonly string[]): boolean;
  matches(requirement: string | string[] | PermissionRequirement): boolean;
  refreshPermissions(): Promise<void>;
  clearPermissions(): void;
  request<T>(path: string, options?: FetchOptions): Promise<T>;
  login(): never | void;
  logout(): Promise<never | void>;
}

export interface AuthorizationRuntimeState {
  state: Ref<AuthorizationState>;
}

export class AuthorizationRequestError extends Error {
  constructor(
    readonly statusCode: number,
    readonly data: unknown,
    message = `Authorization request failed with HTTP ${statusCode}`
  ) {
    super(message);
    this.name = "AuthorizationRequestError";
  }
}

export class AuthenticationRequiredError extends AuthorizationRequestError {
  constructor(data?: unknown) {
    super(401, data, "Authentication is required");
    this.name = "AuthenticationRequiredError";
  }
}
