export interface DemoAccessSummary {
  pageOne: boolean;
  pageTwo: boolean;
  employeeEdit: boolean;
  administration: boolean;
}

export function describeDemoAccess(permissions: readonly string[]): DemoAccessSummary {
  const granted = new Set(permissions);
  return {
    pageOne: granted.has("UI:DEMO_PAGE_1"),
    pageTwo: granted.has("UI:DEMO_PAGE_2"),
    employeeEdit: granted.has("UI:EMPLOYEE_EDIT"),
    administration: granted.has("UI:AUTHORIZATION_ADMIN")
  };
}
