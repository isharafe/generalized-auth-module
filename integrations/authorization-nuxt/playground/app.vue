<script setup lang="ts">
const authorization = useAuthorization();
</script>

<template>
  <main>
    <h1>Nuxt authorization playground</h1>
    <p>Permission state: {{ authorization.status }}</p>
    <Authorized permission="UI:DEMO_SEE_PAGE_1">
      <p>Viewer content</p>
      <template #fallback><p>Viewer content is unavailable.</p></template>
    </Authorized>
    <button v-authorization.disable="'UI:DEMO_SEE_PAGE_2'">Manager action</button>
  </main>
</template>
