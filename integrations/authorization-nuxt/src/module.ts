import {
  addComponent,
  addImports,
  addPlugin,
  addRouteMiddleware,
  addServerHandler,
  createResolver,
  defineNuxtModule
} from "@nuxt/kit";

export interface ModuleOptions {
  backendBaseUrl: string;
  publicBaseUrl: string;
  apiProxyPrefix: string;
  permissionsEndpoint: string;
  csrfEndpoint: string;
  refreshEndpoint: string;
  logoutEndpoint: string;
  loginEndpoint: string;
  deniedRedirect: string;
}

export default defineNuxtModule<ModuleOptions>({
  meta: {
    name: "@isharafe/authorization-nuxt",
    configKey: "authorizationNuxt",
    compatibility: { nuxt: ">=3.21.0" }
  },
  defaults: {
    backendBaseUrl: "",
    publicBaseUrl: "",
    apiProxyPrefix: "/api/_authorization/backend",
    permissionsEndpoint: "/authorization/ui/permissions",
    csrfEndpoint: "/authorization/security/csrf",
    refreshEndpoint: "/authorization/security/token/refresh",
    logoutEndpoint: "/authorization/security/logout",
    loginEndpoint: "",
    deniedRedirect: "/forbidden"
  },
  setup(options, nuxt) {
    validateOptions(options);
    const resolver = createResolver(import.meta.url);
    const privateConfig = {
      backendBaseUrl: options.backendBaseUrl,
      publicBaseUrl: options.publicBaseUrl,
      apiProxyPrefix: normalizedPath(options.apiProxyPrefix),
      permissionsEndpoint: normalizedPath(options.permissionsEndpoint),
      csrfEndpoint: normalizedPath(options.csrfEndpoint),
      refreshEndpoint: normalizedPath(options.refreshEndpoint),
      logoutEndpoint: normalizedPath(options.logoutEndpoint)
    };
    const publicConfig = {
      apiProxyPrefix: privateConfig.apiProxyPrefix,
      permissionsEndpoint: privateConfig.permissionsEndpoint,
      csrfEndpoint: privateConfig.csrfEndpoint,
      refreshEndpoint: privateConfig.refreshEndpoint,
      logoutEndpoint: privateConfig.logoutEndpoint,
      loginEndpoint: options.loginEndpoint ? normalizedPath(options.loginEndpoint) : "",
      deniedRedirect: normalizedPath(options.deniedRedirect)
    };

    nuxt.options.runtimeConfig.authorizationNuxt = {
      ...(nuxt.options.runtimeConfig.authorizationNuxt as object | undefined),
      ...privateConfig
    };
    nuxt.options.runtimeConfig.public.authorizationNuxt = {
      ...(nuxt.options.runtimeConfig.public.authorizationNuxt as object | undefined),
      ...publicConfig
    };

    addPlugin(resolver.resolve("./runtime/plugin"));
    addComponent({
      name: "Authorized",
      filePath: resolver.resolve("./runtime/components/Authorized.vue")
    });
    addImports([
      {
        name: "useAuthorization",
        as: "useAuthorization",
        from: resolver.resolve("./runtime/composables/useAuthorization")
      },
      {
        name: "useAuthorizationFetch",
        as: "useAuthorizationFetch",
        from: resolver.resolve("./runtime/composables/useAuthorizationFetch")
      }
    ]);
    addRouteMiddleware({
      name: "authorization",
      path: resolver.resolve("./runtime/middleware/authorization")
    });

    const proxyHandler = resolver.resolve("./runtime/server/proxy");
    for (const route of new Set([
      privateConfig.permissionsEndpoint,
      privateConfig.csrfEndpoint,
      privateConfig.refreshEndpoint,
      privateConfig.logoutEndpoint
    ])) {
      addServerHandler({ route, handler: proxyHandler });
    }
    addServerHandler({ route: "/oauth2/**:path", handler: proxyHandler });
    addServerHandler({ route: "/login/**:path", handler: proxyHandler });
    addServerHandler({
      route: `${privateConfig.apiProxyPrefix}/**:path`,
      handler: proxyHandler
    });
  }
});

function validateOptions(options: ModuleOptions) {
  for (const [name, value] of Object.entries(options)) {
    if (name === "backendBaseUrl" || name === "publicBaseUrl" || name === "loginEndpoint") continue;
    normalizedPath(value);
  }
  if (options.backendBaseUrl) validateBaseUrl(options.backendBaseUrl, "backendBaseUrl");
  if (options.publicBaseUrl) validateBaseUrl(options.publicBaseUrl, "publicBaseUrl");
}

function normalizedPath(value: string): string {
  if (!value.startsWith("/") || value.startsWith("//") || value.includes("?") || value.includes("#")) {
    throw new Error(`Authorization Nuxt paths must start with one / and contain no query or fragment: ${value}`);
  }
  return value.length > 1 && value.endsWith("/") ? value.slice(0, -1) : value;
}

function validateBaseUrl(value: string, name: string) {
  const parsed = new URL(value);
  if (!new Set(["http:", "https:"]).has(parsed.protocol)
    || parsed.username || parsed.password
    || parsed.pathname !== "/" || parsed.search || parsed.hash) {
    throw new Error(`${name} must be an HTTP(S) origin without credentials, path, query, or fragment`);
  }
}
