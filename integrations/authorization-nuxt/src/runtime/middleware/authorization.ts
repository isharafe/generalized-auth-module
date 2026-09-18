import type { AuthorizationPublicConfig, PermissionMatch } from "../types";

export default defineNuxtRouteMiddleware(async (to) => {
  const requirement = to.meta.authorization as
    | { permissions: string | string[]; match?: PermissionMatch }
    | undefined;
  if (!requirement) return;

  const authorization = useAuthorization();
  if (authorization.status.value === "idle") await authorization.refreshPermissions();
  if (authorization.status.value === "loading") await authorization.refreshPermissions();
  if (authorization.status.value === "error") {
    throw createError({ statusCode: 503, statusMessage: "Authorization unavailable" });
  }
  if (authorization.status.value === "unauthenticated") {
    // A path-scoped refresh cookie is not present on arbitrary SSR page requests. Defer the
    // redirect until hydration gives the browser a chance to call the refresh endpoint.
    if (import.meta.server) return;
    const config = useRuntimeConfig().public.authorizationNuxt as AuthorizationPublicConfig;
    if (!config.loginEndpoint) {
      throw createError({ statusCode: 401, statusMessage: "Authentication required" });
    }
    return navigateTo(config.loginEndpoint, { external: true });
  }

  const permissions = typeof requirement.permissions === "string"
    ? [requirement.permissions]
    : requirement.permissions;
  const allowed = requirement.match === "any"
    ? authorization.canAny(permissions)
    : authorization.canAll(permissions);
  if (!allowed) {
    const config = useRuntimeConfig().public.authorizationNuxt as AuthorizationPublicConfig;
    return navigateTo(config.deniedRedirect);
  }
});

declare module "#app" {
  interface PageMeta {
    authorization?: {
      permissions: string | string[];
      match?: PermissionMatch;
    };
  }
}
