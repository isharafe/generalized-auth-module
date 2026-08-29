import { describe, expect, it, vi } from "vitest";
import { AdminApi } from "./api";

describe("AdminApi", () => {
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
