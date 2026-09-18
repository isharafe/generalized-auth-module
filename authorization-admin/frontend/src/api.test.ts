import { describe, expect, it, vi } from "vitest";
import { AdminApi } from "./api";
import type { RuntimeConfig } from "./types";

describe("AdminApi", () => {
  it("exports and replaces complete authorization data", async () => {
    const bundle = {
      formatVersion: 1,
      exportedAt: "2026-08-29T00:00:00Z",
      permissions: [],
      permissionGroups: [],
      roles: [],
      resourceRules: [],
      users: [],
      externalMappings: [],
      pendingUserAssignments: []
    };
    const fetchMock = vi.fn((input: RequestInfo | URL) =>
      Promise.resolve(
        new Response(
          JSON.stringify(
            String(input).endsWith("/data/export")
              ? bundle
              : {
                  permissions: 0,
                  permissionGroups: 0,
                  roles: 0,
                  resourceRules: 0,
                  users: 0,
                  userRoleAssignments: 0,
                  userPermissionGroupAssignments: 0,
                  externalMappings: 0,
                  pendingUserAssignments: 0
                }
          ),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
    );
    vi.stubGlobal("fetch", fetchMock);
    const api = new AdminApi("/api");

    await expect(api.exportData()).resolves.toEqual(bundle);
    await api.replaceData(bundle);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/api/data/export",
      expect.objectContaining({ method: "POST", credentials: "same-origin" })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/data/import",
      expect.objectContaining({
        method: "POST",
        credentials: "same-origin",
        body: JSON.stringify(bundle)
      })
    );
  });

  it("loads the current authenticated user", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ issuer: "local", subject: "manager", username: "manager" }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(new AdminApi("/api").currentUser()).resolves.toEqual({
      issuer: "local",
      subject: "manager",
      username: "manager"
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/current-user",
      expect.objectContaining({ credentials: "same-origin" })
    );
  });

  it("encodes pagination and search parameters", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({
            content: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );
    vi.stubGlobal("fetch", fetchMock);

    await new AdminApi("/api").page("/roles", {
      search: "finance manager",
      page: 0,
      size: 20
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/roles?search=finance+manager&page=0&size=20",
      expect.objectContaining({ credentials: "same-origin" })
    );
  });

  it("maps the common API error model", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "AUTHZ_CONCURRENT_MODIFICATION",
            message: "The resource changed"
          }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        )
      )
    );

    await expect(
      new AdminApi("/api").put("/roles/HR", { version: 1 })
    ).rejects.toEqual(
      expect.objectContaining({
        status: 409,
        code: "AUTHZ_CONCURRENT_MODIFICATION",
        message: "The resource changed"
      })
    );
  });

  it("sends the runtime CSRF token on unsafe requests", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(null, { status: 204 })
    );
    vi.stubGlobal("fetch", fetchMock);
    const security: RuntimeConfig = {
      apiBasePath: "/api",
      uiBasePath: "/ui",
      cookieOauth2Enabled: true,
      csrfToken: "csrf-value",
      csrfHeaderName: "X-XSRF-TOKEN"
    };

    await new AdminApi("/api", security).post("/roles", { code: "MANAGER" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/roles",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "csrf-value" })
      })
    );
  });

  it("serializes refresh and retries concurrent requests once", async () => {
    let apiCalls = 0;
    let refreshCalls = 0;
    let releaseRefresh!: (response: Response) => void;
    const refreshResponse = new Promise<Response>((resolve) => {
      releaseRefresh = resolve;
    });
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const uri = String(input);
      if (uri === "/authorization/security/token/refresh") {
        refreshCalls += 1;
        return refreshResponse;
      }
      apiCalls += 1;
      if (apiCalls <= 2)
        return Promise.resolve(new Response(null, { status: 401 }));
      return Promise.resolve(
        new Response(
          JSON.stringify({
            issuer: "local",
            subject: "manager",
            username: "manager"
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    const security: RuntimeConfig = {
      apiBasePath: "/api",
      uiBasePath: "/ui",
      cookieOauth2Enabled: true,
      refreshEndpoint: "/authorization/security/token/refresh",
      csrfToken: "csrf-value",
      csrfHeaderName: "X-XSRF-TOKEN"
    };
    const api = new AdminApi("/api", security);

    const first = api.currentUser();
    const second = api.currentUser();
    await vi.waitFor(() => expect(refreshCalls).toBe(1));
    releaseRefresh(new Response(null, { status: 204 }));

    await expect(Promise.all([first, second])).resolves.toEqual([
      { issuer: "local", subject: "manager", username: "manager" },
      { issuer: "local", subject: "manager", username: "manager" }
    ]);
    expect(refreshCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/authorization/security/token/refresh",
      expect.objectContaining({
        method: "POST",
        credentials: "same-origin",
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "csrf-value" })
      })
    );
  });

  it("does not retry the API request when refresh fails", async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL) =>
      Promise.resolve(new Response(null, { status: 401 }))
    );
    vi.stubGlobal("fetch", fetchMock);
    const security: RuntimeConfig = {
      apiBasePath: "/api",
      uiBasePath: "/ui",
      cookieOauth2Enabled: true,
      refreshEndpoint: "/authorization/security/token/refresh",
      csrfToken: "csrf-value",
      csrfHeaderName: "X-XSRF-TOKEN"
    };

    await expect(new AdminApi("/api", security).currentUser()).rejects.toEqual(
      expect.objectContaining({ status: 401 })
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls.filter(([input]) => String(input) === "/api/current-user"))
      .toHaveLength(1);
    expect(
      fetchMock.mock.calls.filter(
        ([input]) => String(input) === "/authorization/security/token/refresh"
      )
    ).toHaveLength(1);
  });
});
