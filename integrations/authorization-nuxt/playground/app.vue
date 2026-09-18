<script setup lang="ts">
const authorization = useAuthorization();
</script>

<template>
  <main>
    <h1>Nuxt authorization playground</h1>
    <p>Permission state: {{ authorization.status }}</p>
    <Authorized permission="UI:EMPLOYEE_DIRECTORY">
      <p>Employee directory</p>
      <template #fallback><p>The employee directory is unavailable.</p></template>
    </Authorized>
    <button v-authorization.disable="'UI:EMPLOYEE_EDIT'">Edit employee</button>
  </main>
</template>
