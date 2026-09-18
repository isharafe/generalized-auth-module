<script setup lang="ts">
definePageMeta({
  middleware: "authorization",
  authorization: { permissions: "UI:DEMO_PAGE_1" }
});
</script>

<template>
  <main class="page-shell narrow-page">
    <p class="eyebrow">UI:DEMO_PAGE_1</p>
    <h1>Viewer page</h1>
    <p>This route is guarded by the Nuxt authorization middleware. Both viewer and manager can open it.</p>
    <NuxtLink class="button primary" to="/">Return to dashboard</NuxtLink>
  </main>
</template>
