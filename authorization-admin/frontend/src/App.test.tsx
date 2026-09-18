import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdminApi } from "./api";
import { App } from "./App";

const config = {
  apiBasePath: "/authorization-admin/api",
  uiBasePath: "/authorization-admin"
};

const currentUser = {
  issuer: "http://localhost:8081/realms/employee-demo",
  subject: "user-123",
  username: "manager",
  email: "manager@example.com",
  firstName: "Demo",
  lastName: "Manager"
};

function page(content: unknown[] = [], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: totalElements ? 1 : 0 };
}

function response(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" }
    })
  );
}

describe("Authorization admin UI", () => {
  beforeEach(() => {
    window.location.hash = "#/dashboard";
    vi.spyOn(AdminApi.prototype, "currentUser").mockResolvedValue(currentUser);
  });

  it("loads capabilities, renders the dashboard, and hides unsupported sync", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "DATABASE",
            identitySynchronization: false,
            externalAuthorityMapping: true,
            syncProvider: null
          });
        if (url.includes("/audit")) return response(page([]));
        if (url.includes("/users")) return response(page([], 2));
        if (url.includes("/roles")) return response(page([], 3));
        if (url.includes("/permission-groups")) return response(page([], 4));
        if (url.includes("/permissions")) return response(page([], 5));
        if (url.includes("/resource-rules")) return response(page([], 6));
        throw new Error(`Unexpected request ${url}`);
      })
    );

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(await screen.findByText("Local authorization, clearly explained.")).toBeVisible();
    expect(screen.getByText("Demo Manager")).toBeVisible();
    expect(screen.getByText("manager@example.com")).toBeVisible();
    expect(screen.getAllByText("DATABASE")).not.toHaveLength(0);
    expect(screen.queryByRole("link", { name: /Synchronization/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /External mappings/ })).toBeVisible();
  });

  it("renders a CSRF-protected OIDC logout action when cookie security is enabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "KEYCLOAK",
            identitySynchronization: true,
            externalAuthorityMapping: true,
            syncProvider: "keycloak"
          });
        if (url.includes("/audit")) return response(page([]));
        return response(page([]));
      })
    );
    const secureConfig = {
      ...config,
      cookieOauth2Enabled: true,
      logoutEndpoint: "/authorization/security/logout",
      logoutMode: "OIDC" as const,
      csrfParameterName: "_csrf",
      csrfToken: "csrf-value"
    };

    const { container } = render(
      <App config={secureConfig} api={new AdminApi(config.apiBasePath)} />
    );

    const button = await screen.findByRole("button", { name: "Sign out everywhere" });
    const form = button.closest("form");
    expect(form).toHaveAttribute("action", "/authorization/security/logout");
    expect(container.querySelector('input[name="_csrf"]')).toHaveValue("csrf-value");
  });

  it("shows the structured URL permission editor and preview", async () => {
    window.location.hash = "#/permissions";
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "DATABASE",
            identitySynchronization: false,
            externalAuthorityMapping: true,
            syncProvider: null
          });
        if (url.includes("/permissions"))
          return response(
            page([
              {
                code: "URL:EMPLOYEE_VIEW",
                name: "View employees",
                resourceType: "URL",
                pattern: "GET:/employees/**",
                enabled: true,
                version: 0
              }
            ])
          );
        throw new Error(`Unexpected request ${url}`);
      })
    );

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(await screen.findByText("URL:EMPLOYEE_VIEW")).toBeVisible();
    expect(screen.getByText("Canonical code")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Local code"), {
      target: { value: "EMPLOYEE_EDIT" }
    });
    expect(screen.getByText("URL:EMPLOYEE_EDIT")).toBeVisible();
    expect(screen.getByText("Permission preview")).toBeVisible();
    expect(screen.getByText("URL:GET:/api/**")).toBeVisible();
  });

  it("shows the resource type for resource rules", async () => {
    window.location.hash = "#/rules";
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/capabilities"))
        return response({ source: "DATABASE", identitySynchronization: false,
          externalAuthorityMapping: true, syncProvider: null });
      if (url.includes("/resource-rules"))
        return response(page([{ code: "EMPLOYEE_API", resourceType: "URL",
          pattern: "*:/employees/**", accessMode: "AUTHORIZED", priority: 0,
          enabled: true, version: 0 }]));
      throw new Error(`Unexpected request ${url}`);
    }));
    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    const rule = (await screen.findByText("EMPLOYEE_API")).closest("article");
    expect(rule).not.toBeNull();
    expect(within(rule!).getByText("URL")).toBeVisible();
  });

  it("shows synchronization only when the provider supports it", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "KEYCLOAK",
            identitySynchronization: true,
            externalAuthorityMapping: true,
            syncProvider: "keycloak"
          });
        if (url.includes("/audit")) return response(page([]));
        return response(page([]));
      })
    );

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(
      await screen.findByRole("link", { name: /Synchronization/ })
    ).toBeVisible();
  });

  it("defaults external mappings to LDAP and offers attribute authorities", async () => {
    window.location.hash = "#/mappings";
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "LDAP",
            identitySynchronization: true,
            externalAuthorityMapping: true,
            syncProvider: "ldap"
          });
        if (url.includes("/external-mappings")) return response(page([]));
        throw new Error(`Unexpected request ${url}`);
      })
    );

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(await screen.findByDisplayValue("LDAP")).toBeVisible();
    expect(screen.getByRole("option", { name: "ATTRIBUTE" })).toBeInTheDocument();
  });

  it("labels and filters audit event kinds", async () => {
    window.location.hash = "#/audit";
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/capabilities"))
        return response({
          source: "DATABASE",
          identitySynchronization: false,
          externalAuthorityMapping: true,
          syncProvider: null
        });
      if (url.includes("/audit"))
        return response(
          page([
            {
              id: 1,
              timestamp: "2026-08-27T12:00:00Z",
              eventKind: "DECISION",
              eventType: "AUTHORIZATION_DENIED",
              actorSubject: "alice",
              target: "/employees",
              decision: "DENIED"
            }
          ])
        );
      throw new Error(`Unexpected request ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(await screen.findByText("DECISION")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Event kind"), {
      target: { value: "CHANGE" }
    });

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([input]) =>
          String(input).includes("eventKind=CHANGE")
        )
      ).toBe(true)
    );
  });

  it("warns that importing is a destructive full replacement", async () => {
    window.location.hash = "#/data";
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/capabilities"))
          return response({
            source: "DATABASE",
            identitySynchronization: false,
            externalAuthorityMapping: true,
            syncProvider: null
          });
        throw new Error(`Unexpected request ${url}`);
      })
    );

    render(<App config={config} api={new AdminApi(config.apiBasePath)} />);

    expect(await screen.findByText("This is a full replacement.")).toBeVisible();
    expect(
      screen.getByText(/all current roles, permission groups, permissions/)
    ).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Download full export" })
    ).toBeVisible();
  });
});
