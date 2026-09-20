import type { FetchOptions, FetchResponse } from "ofetch";
import { computed, type Ref } from "vue";
import { matchesPermissions, validatePermissionCode } from "./permissions";
import {
  AuthenticationRequiredError,
  AuthorizationRequestError,
  type AuthorizationManager,
  type AuthorizationPublicConfig,
  type AuthorizationState,
  type CsrfResponse,
  type PermissionRequirement,
  type PermissionsResponse
} from "./types";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

export function createAuthorizationManager(
  fetcher: AuthorizationFetcher,
  config: AuthorizationPublicConfig,
  state: Ref<AuthorizationState>,
  options: AuthorizationManagerOptions = {}
): AuthorizationManager {
  let csrf: CsrfResponse | null = null;
  let csrfInFlight: Promise<CsrfResponse> | null = null;
  let refreshInFlight: Promise<boolean> | null = null;
  let permissionsInFlight: Promise<void> | null = null;

  const granted = () => new Set(state.value.permissions);

  const clearPermissions = () => {
    state.value = {
      permissions: [],
      entitlementVersion: 0,
      status: "unauthenticated",
      error: null
    };
  };

  const ensureCsrf = async (): Promise<CsrfResponse> => {
    if (csrf) return csrf;
    if (!csrfInFlight) {
      csrfInFlight = fetcher.raw<CsrfResponse>(config.csrfEndpoint, {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        ignoreResponseError: true
      }).then((response) => {
        if (!response.ok) throw requestError(response);
        const value = response._data;
        if (!value?.token || !value.headerName || !value.parameterName) {
          throw new AuthorizationRequestError(503, value, "Invalid CSRF response");
        }
        csrf = value;
        return value;
      }).finally(() => {
        csrfInFlight = null;
      });
    }
    return csrfInFlight;
  };

  const performRaw = async <T>(
    path: string,
    options: FetchOptions = {}
  ): Promise<FetchResponse<T>> => {
    const method = String(options.method ?? "GET").toUpperCase();
    const headers = new Headers(options.headers as HeadersInit | undefined);
    headers.set("Accept", headers.get("Accept") ?? "application/json");
    if (!SAFE_METHODS.has(method)) {
      const token = await ensureCsrf();
      headers.set(token.headerName, token.token);
    }
    return fetcher.raw<T>(path, {
      ...options,
      method,
      credentials: "same-origin",
      headers,
      ignoreResponseError: true
    } as FetchOptions<"json">);
  };

  const readResponse = <T>(response: FetchResponse<T>): T => {
    if (response.status === 401) throw new AuthenticationRequiredError(response._data);
    if (!response.ok) throw requestError(response);
    return response._data as T;
  };

  const performLoadPermissions = async (allowRefresh: boolean): Promise<void> => {
    state.value = { ...state.value, status: "loading", error: null };
    let response = await performRaw<PermissionsResponse>(config.permissionsEndpoint);
    if (response.status === 401 && allowRefresh && await refreshAuthentication()) {
      response = await performRaw<PermissionsResponse>(config.permissionsEndpoint);
    }
    if (response.status === 401) {
      clearPermissions();
      return;
    }
    if (!response.ok) {
      const failure = requestError(response);
      state.value = {
        permissions: [],
        entitlementVersion: 0,
        status: "error",
        error: failure.message
      };
      return;
    }
    const body = response._data;
    if (!body || !Array.isArray(body.permissions)
      || !Number.isSafeInteger(body.entitlementVersion) || body.entitlementVersion < 0) {
      state.value = {
        permissions: [],
        entitlementVersion: 0,
        status: "error",
        error: "Invalid permissions response"
      };
      return;
    }
    try {
      state.value = {
        permissions: [...new Set(body.permissions.map(validatePermissionCode))].sort(),
        entitlementVersion: body.entitlementVersion,
        status: "ready",
        error: null
      };
    } catch (failure) {
      state.value = {
        permissions: [],
        entitlementVersion: 0,
        status: "error",
        error: failure instanceof Error ? failure.message : "Invalid permissions response"
      };
    }
  };

  const loadPermissions = (allowRefresh: boolean): Promise<void> => {
    if (!permissionsInFlight) {
      permissionsInFlight = performLoadPermissions(allowRefresh).finally(() => {
        permissionsInFlight = null;
      });
    }
    return permissionsInFlight;
  };

  const refreshAuthentication = async (): Promise<boolean> => {
    if (options.refreshEnabled === false) return false;
    if (!refreshInFlight) {
      refreshInFlight = (async () => {
        try {
          const response = await performRaw<unknown>(config.refreshEndpoint, { method: "POST" });
          if (!response.ok) {
            clearPermissions();
            return false;
          }
          return true;
        } catch {
          clearPermissions();
          return false;
        }
      })().finally(() => {
        refreshInFlight = null;
      });
    }
    return refreshInFlight;
  };

  const request = async <T>(path: string, options: FetchOptions = {}): Promise<T> => {
    const target = proxyTarget(config.apiProxyPrefix, path);
    let response = await performRaw<T>(target, options);
    if (response.status === 401) {
      if (!await refreshAuthentication()) throw new AuthenticationRequiredError(response._data);
      await loadPermissions(false);
      response = await performRaw<T>(target, options);
    }
    return readResponse(response);
  };

  const manager: AuthorizationManager = {
    permissions: computed(() => state.value.permissions),
    entitlementVersion: computed(() => state.value.entitlementVersion),
    status: computed(() => state.value.status),
    error: computed(() => state.value.error),
    can(permission) {
      if (state.value.status !== "ready") return false;
      return granted().has(validatePermissionCode(permission));
    },
    canAll(permissions) {
      if (state.value.status !== "ready") return false;
      return permissions.length > 0
        && permissions.map(validatePermissionCode).every((permission) => granted().has(permission));
    },
    canAny(permissions) {
      if (state.value.status !== "ready") return false;
      return permissions.map(validatePermissionCode).some((permission) => granted().has(permission));
    },
    matches(requirement: string | string[] | PermissionRequirement) {
      if (state.value.status !== "ready") return false;
      return matchesPermissions(granted(), requirement);
    },
    refreshPermissions: () => loadPermissions(true),
    clearPermissions,
    request,
    login() {
      if (!config.loginEndpoint) {
        throw new Error("authorizationNuxt.loginEndpoint is required to start login");
      }
      if (import.meta.client) window.location.assign(config.loginEndpoint);
    },
    async logout() {
      const token = await ensureCsrf();
      clearPermissions();
      if (!import.meta.client) return;
      const form = document.createElement("form");
      form.method = "post";
      form.action = config.logoutEndpoint;
      const input = document.createElement("input");
      input.type = "hidden";
      input.name = token.parameterName;
      input.value = token.token;
      form.append(input);
      document.body.append(form);
      form.submit();
    }
  };
  return manager;
}

export interface AuthorizationFetcher {
  raw<T>(request: string, options?: FetchOptions<"json">): Promise<FetchResponse<T>>;
}

export interface AuthorizationManagerOptions {
  refreshEnabled?: boolean;
}

function proxyTarget(prefix: string, path: string): string {
  if (!path.startsWith("/") || path.startsWith("//") || path.includes("\\")) {
    throw new Error(`Authorization backend paths must start with one /: ${path}`);
  }
  const pathname = path.split(/[?#]/, 1)[0] ?? path;
  try {
    if (pathname.split("/").some((segment) => decodeURIComponent(segment) === "..")) {
      throw new Error("parent traversal");
    }
  } catch {
    throw new Error("Authorization backend paths must not contain invalid encoding or parent traversal");
  }
  return `${prefix}${path}`;
}

function requestError(response: FetchResponse<unknown>): AuthorizationRequestError {
  const data = response._data;
  const detail = data && typeof data === "object" && "message" in data
    ? String(data.message)
    : `Authorization request failed with HTTP ${response.status}`;
  return new AuthorizationRequestError(response.status, data, detail);
}
