import type { FetchOptions, FetchResponse } from "ofetch";
import { ref } from "vue";
import { describe, expect, it } from "vitest";
import {
  createAuthorizationManager,
  type AuthorizationFetcher
} from "../src/runtime/manager";
import type { AuthorizationPublicConfig, AuthorizationState } from "../src/runtime/types";

const config: AuthorizationPublicConfig = {
  apiProxyPrefix: "/api/_authorization/backend",
  permissionsEndpoint: "/authorization/user/permissions",
  csrfEndpoint: "/authorization/security/csrf",
  refreshEndpoint: "/authorization/security/token/refresh",
  logoutEndpoint: "/authorization/security/logout",
  loginEndpoint: "/oauth2/authorization/keycloak",
  deniedRedirect: "/forbidden"
};

function state() {
  return ref<AuthorizationState>({
    permissions: [],
    entitlementVersion: 0,
    status: "idle",
    error: null
  });
}

function response<T>(status: number, data?: T): FetchResponse<T> {
  return Object.assign(new Response(null, { status }), { _data: data }) as FetchResponse<T>;
}

function fetcher(
  raw: (path: string, options: FetchOptions) => Promise<FetchResponse<unknown>>,
  csrf = { token: "csrf", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" }
): AuthorizationFetcher {
  return {
    raw: ((path: string, options: FetchOptions) => path === config.csrfEndpoint
      ? Promise.resolve(response(200, csrf))
      : raw(path, options)) as AuthorizationFetcher["raw"]
  };
}

describe("authorization manager", () => {
  it("loads effective permissions and fails closed until ready", async () => {
    const authorizationState = state();
    const manager = createAuthorizationManager(
      fetcher(async () => response(200, {
        permissions: ["URL:EDIT", "UI:VIEW", "URL:EDIT"],
        entitlementVersion: 3
      })),
      config,
      authorizationState
    );

    expect(manager.can("UI:VIEW")).toBe(false);
    await manager.refreshPermissions();

    expect(manager.permissions.value).toEqual(["UI:VIEW", "URL:EDIT"]);
    expect(manager.entitlementVersion.value).toBe(3);
    expect(manager.can("UI:VIEW")).toBe(true);
    expect(manager.canAll(["UI:VIEW", "URL:EDIT"])).toBe(true);
  });

  it("adds CSRF to unsafe API requests", async () => {
    let sentHeader: string | null = null;
    const manager = createAuthorizationManager(
      fetcher(async (_path, options) => {
        sentHeader = new Headers(options.headers as HeadersInit).get("X-XSRF-TOKEN");
        return response(200, { saved: true });
      }),
      config,
      state()
    );

    await expect(manager.request("/employees", { method: "POST", body: { name: "Ada" } }))
      .resolves.toEqual({ saved: true });
    expect(sentHeader).toBe("csrf");
  });

  it("serializes refresh and retries concurrent requests once", async () => {
    let refreshCalls = 0;
    let apiCalls = 0;
    let permissionCalls = 0;
    const manager = createAuthorizationManager(
      fetcher(async (path) => {
        if (path === config.refreshEndpoint) {
          refreshCalls += 1;
          await Promise.resolve();
          return response(204);
        }
        if (path === config.permissionsEndpoint) {
          permissionCalls += 1;
          return response(200, { permissions: ["UI:VIEW"], entitlementVersion: 4 });
        }
        apiCalls += 1;
        return apiCalls <= 2 ? response(401) : response(200, { ok: true });
      }),
      config,
      state()
    );

    await expect(Promise.all([
      manager.request("/one"),
      manager.request("/two")
    ])).resolves.toEqual([{ ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
    expect(permissionCalls).toBe(1);
  });

  it("does not refresh authentication when refresh is disabled for SSR", async () => {
    let refreshCalls = 0;
    const authorizationState = state();
    const manager = createAuthorizationManager(
      fetcher(async (path) => {
        if (path === config.refreshEndpoint) refreshCalls += 1;
        return response(401);
      }),
      config,
      authorizationState,
      { refreshEnabled: false }
    );

    await manager.refreshPermissions();

    expect(refreshCalls).toBe(0);
    expect(authorizationState.value.status).toBe("unauthenticated");
  });

  it("fails closed when the permission response contains a malformed code", async () => {
    const authorizationState = state();
    const manager = createAuthorizationManager(
      fetcher(async () => response(200, {
        permissions: ["EMPLOYEE_VIEW"],
        entitlementVersion: 1
      })),
      config,
      authorizationState
    );

    await manager.refreshPermissions();

    expect(authorizationState.value.status).toBe("error");
    expect(manager.can("UI:EMPLOYEE_VIEW")).toBe(false);
  });
});
