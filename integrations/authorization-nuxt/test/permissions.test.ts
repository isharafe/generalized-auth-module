import { describe, expect, it } from "vitest";
import { matchesPermissions, validatePermissionCode } from "../src/runtime/permissions";

describe("permission matching", () => {
  const granted = new Set(["URL:VIEW", "UI:EDIT"]);

  it("requires every permission by default", () => {
    expect(matchesPermissions(granted, ["URL:VIEW", "UI:EDIT"])).toBe(true);
    expect(matchesPermissions(granted, ["URL:VIEW", "UI:DELETE"])).toBe(false);
  });

  it("supports explicit any matching", () => {
    expect(matchesPermissions(granted, {
      permissions: ["UI:DELETE", "UI:EDIT"],
      match: "any"
    })).toBe(true);
  });

  it("rejects unqualified permission codes", () => {
    expect(() => validatePermissionCode("EDIT")).toThrow(/TYPE:<code>/);
  });

  it("accepts current and future canonical resource type prefixes", () => {
    expect(validatePermissionCode("URL:EMPLOYEE_EDIT")).toBe("URL:EMPLOYEE_EDIT");
    expect(validatePermissionCode("UI:EMPLOYEE_EDIT")).toBe("UI:EMPLOYEE_EDIT");
    expect(validatePermissionCode("ENTITY:EMPLOYEE_EDIT")).toBe("ENTITY:EMPLOYEE_EDIT");
  });

  it("rejects invalid or oversized permission codes", () => {
    expect(() => validatePermissionCode("url:EMPLOYEE_EDIT")).toThrow(/TYPE:<code>/);
    expect(() => validatePermissionCode(`URL:${"A".repeat(97)}`)).toThrow(/100 characters/);
  });
});
