<script setup lang="ts">
definePageMeta({
  middleware: "authorization",
  authorization: { permissions: "UI:EMPLOYEE_DIRECTORY" }
});
</script>

<template>
  <main class="page-shell narrow-page">
    <p class="eyebrow">UI:EMPLOYEE_DIRECTORY</p>
    <h1>Employee directory</h1>
    <p>
      This route is guarded by the Nuxt authorization middleware. HR analysts and HR managers can
      open it.
    </p>
    <NuxtLink class="button primary" to="/">Return to dashboard</NuxtLink>
  </main>
</template>
