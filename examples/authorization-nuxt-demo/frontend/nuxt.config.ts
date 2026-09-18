export default defineNuxtConfig({
  modules: ["@isharafe/authorization-nuxt/source"],
  css: ["~/assets/main.css"],
  devtools: { enabled: false },
  compatibilityDate: "2026-09-18",
  authorizationNuxt: {
    backendBaseUrl: process.env.AUTHORIZATION_BACKEND_URL ?? "http://localhost:8082",
    publicBaseUrl: process.env.NUXT_PUBLIC_BASE_URL ?? "http://localhost:3000",
    loginEndpoint: "/oauth2/authorization/keycloak",
    deniedRedirect: "/forbidden",
    backendProxyPrefixes: ["/authorization-admin"]
  }
});
