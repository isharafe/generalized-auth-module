<script setup lang="ts">
definePageMeta({
  middleware: "authorization",
  authorization: { permissions: "UI:DEMO_PAGE_2" }
});
</script>

<template>
  <main class="page-shell narrow-page manager-page">
    <p class="eyebrow">UI:DEMO_PAGE_2</p>
    <h1>Manager page</h1>
    <p>Only the manager receives this UI permission from synchronized local assignments.</p>
    <NuxtLink class="button primary" to="/">Return to dashboard</NuxtLink>
  </main>
</template>
