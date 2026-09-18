<script setup lang="ts">
definePageMeta({
  middleware: "authorization",
  authorization: { permissions: "UI:MANAGER_WORKSPACE" }
});
</script>

<template>
  <main class="page-shell narrow-page manager-workspace">
    <p class="eyebrow">UI:MANAGER_WORKSPACE</p>
    <h1>Manager workspace</h1>
    <p>Only HR managers receive this UI permission from synchronized local assignments.</p>
    <NuxtLink class="button primary" to="/">Return to dashboard</NuxtLink>
  </main>
</template>
