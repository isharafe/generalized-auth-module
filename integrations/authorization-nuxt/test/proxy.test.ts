import { describe, expect, it } from "vitest";
import { normalizeBackendProxyPrefixes } from "../src/module";
import { proxyTarget } from "../src/runtime/server/proxy";

const config = {
  backendBaseUrl: "http://spring:8080",
  publicBaseUrl: "https://app.example.com",
  apiProxyPrefix: "/api/_authorization/backend"
};

describe("Nitro proxy targets", () => {
  it("maps the fixed API proxy to the configured backend", () => {
    expect(proxyTarget(config, "/api/_authorization/backend/demo/employees?active=true"))
      .toBe("http://spring:8080/demo/employees?active=true");
  });

  it("preserves framework and OAuth paths", () => {
    expect(proxyTarget(config, "/authorization/security/csrf"))
      .toBe("http://spring:8080/authorization/security/csrf");
    expect(proxyTarget(config, "/oauth2/authorization/keycloak"))
      .toBe("http://spring:8080/oauth2/authorization/keycloak");
    expect(proxyTarget(config, "/authorization-admin/assets/index.js"))
      .toBe("http://spring:8080/authorization-admin/assets/index.js");
  });

  it("rejects traversal and protocol-relative targets", () => {
    expect(() => proxyTarget(config, "/api/_authorization/backend/%2e%2e/admin"))
      .toThrow();
    expect(() => proxyTarget(config, "/api/_authorization/backend//evil.example/path"))
      .toThrow();
  });

  it("rejects backend URLs that are not origins", () => {
    expect(() => proxyTarget({ ...config, backendBaseUrl: "http://spring:8080/context" }, "/api"))
      .toThrow();
  });

  it("normalizes safe additional backend proxy prefixes", () => {
    expect(normalizeBackendProxyPrefixes(
      ["/authorization-admin/", "/authorization-admin"],
      config.apiProxyPrefix
    )).toEqual(["/authorization-admin"]);
  });

  it("rejects root and API-overlapping backend proxy prefixes", () => {
    expect(() => normalizeBackendProxyPrefixes(["/"], config.apiProxyPrefix)).toThrow(/root/);
    expect(() => normalizeBackendProxyPrefixes(
      ["/api/_authorization"],
      config.apiProxyPrefix
    )).toThrow(/conflicts/);
  });
});
