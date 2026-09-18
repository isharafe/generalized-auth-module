import { watchEffect, type DirectiveBinding, type VNode } from "vue";
import {
  createAuthorizationManager,
  type AuthorizationFetcher
} from "./manager";
import type {
  AuthorizationManager,
  AuthorizationPublicConfig,
  AuthorizationState,
  PermissionRequirement
} from "./types";

export default defineNuxtPlugin(async (nuxtApp) => {
  const config = useRuntimeConfig().public.authorizationNuxt as AuthorizationPublicConfig;
  const state = useState<AuthorizationState>("authorization-nuxt:state", () => ({
    permissions: [],
    entitlementVersion: 0,
    status: "idle",
    error: null
  }));
  const requestHeaders = import.meta.server
    ? useRequestHeaders(["cookie", "authorization"])
    : undefined;
  const manager = createAuthorizationManager(
    $fetch.create({ headers: requestHeaders }) as unknown as AuthorizationFetcher,
    config,
    state,
    { refreshEnabled: import.meta.client }
  );
  nuxtApp.provide("authorization", manager);
  nuxtApp.provide("authorizationFetch", manager.request);
  nuxtApp.vueApp.directive("authorization", authorizationDirective(manager));

  if (state.value.status !== "ready") await manager.refreshPermissions();
});

function authorizationDirective(manager: AuthorizationManager) {
  const stops = new WeakMap<HTMLElement, () => void>();
  const originals = new WeakMap<HTMLElement, { hidden: boolean; disabled?: boolean }>();

  const requirement = (binding: DirectiveBinding): string | string[] | PermissionRequirement =>
    binding.value as string | string[] | PermissionRequirement;

  const apply = (element: HTMLElement, binding: DirectiveBinding) => {
    const allowed = manager.matches(requirement(binding));
    const original = originals.get(element) ?? { hidden: element.hidden };
    if (binding.modifiers.disable) {
      if ("disabled" in element) {
        (element as HTMLElement & { disabled: boolean }).disabled = Boolean(original.disabled) || !allowed;
      }
      if (!allowed) element.setAttribute("aria-disabled", "true");
      else element.removeAttribute("aria-disabled");
    } else {
      element.hidden = original.hidden || !allowed;
    }
  };

  return {
    getSSRProps(binding: DirectiveBinding) {
      const allowed = manager.matches(requirement(binding));
      if (allowed) return {};
      return binding.modifiers.disable
        ? { disabled: true, "aria-disabled": "true", "data-authorization-denied": "true" }
        : { hidden: true, "data-authorization-denied": "true" };
    },
    mounted(element: HTMLElement, binding: DirectiveBinding, vnode: VNode) {
      originals.set(element, {
        hidden: Boolean(vnode.props?.hidden),
        disabled: "disabled" in element ? Boolean(vnode.props?.disabled) : undefined
      });
      const stop = watchEffect(() => apply(element, binding));
      stops.set(element, stop);
    },
    updated(element: HTMLElement, binding: DirectiveBinding) {
      apply(element, binding);
    },
    unmounted(element: HTMLElement) {
      stops.get(element)?.();
      stops.delete(element);
      originals.delete(element);
    }
  };
}

declare module "#app" {
  interface NuxtApp {
    $authorization: AuthorizationManager;
    $authorizationFetch: AuthorizationManager["request"];
  }
}

declare module "vue" {
  interface ComponentCustomProperties {
    $authorization: AuthorizationManager;
    $authorizationFetch: AuthorizationManager["request"];
  }
}
