import { describe, expect, it } from "vitest";
import { matchesPermissions, validatePermissionCode } from "../src/runtime/permissions";

describe("permission matching", () => {
  const granted = new Set(["UI:VIEW", "UI:EDIT"]);

  it("requires every permission by default", () => {
    expect(matchesPermissions(granted, ["UI:VIEW", "UI:EDIT"])).toBe(true);
    expect(matchesPermissions(granted, ["UI:VIEW", "UI:DELETE"])).toBe(false);
  });

  it("supports explicit any matching", () => {
    expect(matchesPermissions(granted, {
      permissions: ["UI:DELETE", "UI:EDIT"],
      match: "any"
    })).toBe(true);
  });

  it("rejects unqualified permission codes", () => {
    expect(() => validatePermissionCode("EDIT")).toThrow(/UI:<code>/);
  });
});
