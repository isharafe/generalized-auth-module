export default defineNuxtConfig({
  modules: ["../src/module"],
  authorizationNuxt: {
    backendBaseUrl: process.env.AUTHORIZATION_BACKEND_URL ?? "http://localhost:8080",
    publicBaseUrl: process.env.NUXT_PUBLIC_BASE_URL ?? "http://localhost:3000",
    loginEndpoint: "/oauth2/authorization/keycloak"
  }
});
