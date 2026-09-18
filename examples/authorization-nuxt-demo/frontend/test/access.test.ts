import { describe, expect, it } from "vitest";
import { describeDemoAccess } from "../utils/access";

describe("demo authorization presentation", () => {
  it("models HR analyst presentation", () => {
    expect(describeDemoAccess(["UI:EMPLOYEE_DIRECTORY"])).toEqual({
      employeeDirectory: true,
      managerWorkspace: false,
      employeeEdit: false,
      administration: false
    });
  });

  it("models HR manager presentation", () => {
    expect(describeDemoAccess([
      "UI:EMPLOYEE_DIRECTORY",
      "UI:MANAGER_WORKSPACE",
      "UI:EMPLOYEE_EDIT"
    ])).toEqual({
      employeeDirectory: true,
      managerWorkspace: true,
      employeeEdit: true,
      administration: false
    });
  });

  it("models administrator and fail-closed presentation", () => {
    expect(describeDemoAccess(["UI:AUTHORIZATION_ADMIN"]).administration).toBe(true);
    expect(describeDemoAccess([])).toEqual({
      employeeDirectory: false,
      managerWorkspace: false,
      employeeEdit: false,
      administration: false
    });
  });
});
