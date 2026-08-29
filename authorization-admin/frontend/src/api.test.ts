import { describe, expect, it, vi } from "vitest";
import { AdminApi } from "./api";

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
});
