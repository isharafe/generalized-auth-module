import type { AuthorizationManager } from "../types";

export function useAuthorization(): AuthorizationManager {
  return useNuxtApp().$authorization;
}
