import { describe, expect, it } from "vitest";
import { describeDemoAccess } from "../utils/access";

describe("demo authorization presentation", () => {
  it("models viewer presentation", () => {
    expect(describeDemoAccess(["UI:DEMO_PAGE_1"])).toEqual({
      pageOne: true,
      pageTwo: false,
      employeeEdit: false,
      administration: false
    });
  });

  it("models manager presentation", () => {
    expect(describeDemoAccess([
      "UI:DEMO_PAGE_1",
      "UI:DEMO_PAGE_2",
      "UI:EMPLOYEE_EDIT"
    ])).toEqual({
      pageOne: true,
      pageTwo: true,
      employeeEdit: true,
      administration: false
    });
  });

  it("models administrator and fail-closed presentation", () => {
    expect(describeDemoAccess(["UI:AUTHORIZATION_ADMIN"]).administration).toBe(true);
    expect(describeDemoAccess([])).toEqual({
      pageOne: false,
      pageTwo: false,
      employeeEdit: false,
      administration: false
    });
  });
});
