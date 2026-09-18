export interface DemoAccessSummary {
  employeeDirectory: boolean;
  managerWorkspace: boolean;
  employeeEdit: boolean;
  administration: boolean;
}

export function describeDemoAccess(permissions: readonly string[]): DemoAccessSummary {
  const granted = new Set(permissions);
  return {
    employeeDirectory: granted.has("UI:EMPLOYEE_DIRECTORY"),
    managerWorkspace: granted.has("UI:MANAGER_WORKSPACE"),
    employeeEdit: granted.has("UI:EMPLOYEE_EDIT"),
    administration: granted.has("UI:AUTHORIZATION_ADMIN")
  };
}
