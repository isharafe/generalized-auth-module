<script setup lang="ts">
const authorization = useAuthorization();
const status = authorization.status;
const permissions = authorization.permissions;

function signIn() {
  authorization.login();
}
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <NuxtLink class="brand" to="/">
        <span class="brand-mark">A</span>
        <span>Nuxt authorization demo</span>
      </NuxtLink>
      <nav class="site-nav" aria-label="Primary navigation">
        <NuxtLink to="/">Dashboard</NuxtLink>
        <Authorized permission="UI:DEMO_PAGE_1">
          <NuxtLink to="/page-one">Page one</NuxtLink>
        </Authorized>
        <Authorized permission="UI:DEMO_PAGE_2">
          <NuxtLink to="/page-two">Page two</NuxtLink>
        </Authorized>
        <Authorized permission="UI:AUTHORIZATION_ADMIN">
          <a href="/authorization-admin/">Admin UI</a>
        </Authorized>
      </nav>
      <div class="session-actions">
        <span class="status-pill" :data-status="status">{{ status }}</span>
        <button v-if="status === 'unauthenticated'" class="button secondary" @click="signIn">
          Sign in
        </button>
        <button v-else-if="status === 'ready'" class="text-button" @click="authorization.logout()">
          Sign out
        </button>
      </div>
    </header>

    <NuxtPage />

    <footer class="site-footer">
      <span>Effective UI permissions: {{ permissions.length }}</span>
      <span>Spring remains the enforcement boundary.</span>
    </footer>
  </div>
</template>
