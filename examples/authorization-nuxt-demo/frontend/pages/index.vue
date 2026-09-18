<script setup lang="ts">
import type { Employee, Profile, PublicStatus } from "~/types/demo";
import { describeDemoAccess } from "~/utils/access";

const authorization = useAuthorization();
const { data: backend } = await useAuthorizationFetch<PublicStatus>("/demo/public", {
  key: "nuxt-demo-public-status"
});
const profile = ref<Profile | null>(null);
const employees = ref<Employee[]>([]);
const drafts = reactive<Record<number, string>>({});
const requestError = ref<string | null>(null);
const saving = ref<number | null>(null);
const access = computed(() => describeDemoAccess(authorization.permissions.value));

watch(
  () => authorization.status.value,
  async (status) => {
    if (status !== "ready") {
      profile.value = null;
      employees.value = [];
      return;
    }
    await loadProtectedData();
  },
  { immediate: true }
);

async function loadProtectedData() {
  requestError.value = null;
  try {
    profile.value = await authorization.request<Profile>("/demo/profile");
    if (!access.value.pageOne) {
      employees.value = [];
      return;
    }
    const loadedEmployees = await authorization.request<Employee[]>("/demo/employees");
    employees.value = loadedEmployees;
    for (const employee of loadedEmployees) drafts[employee.id] = employee.name;
  } catch (failure) {
    requestError.value = failure instanceof Error ? failure.message : "Unable to load protected data";
  }
}

async function save(employee: Employee) {
  saving.value = employee.id;
  requestError.value = null;
  try {
    const updated = await authorization.request<Employee>(`/demo/employees/${employee.id}`, {
      method: "PUT",
      body: { name: drafts[employee.id] }
    });
    employees.value = employees.value.map((value) => value.id === updated.id ? updated : value);
  } catch (failure) {
    requestError.value = failure instanceof Error ? failure.message : "Employee update failed";
  } finally {
    saving.value = null;
  }
}
</script>

<template>
  <main class="page-shell">
    <section class="hero">
      <div>
        <p class="eyebrow">SPRING SECURITY · NUXT · NITRO</p>
        <h1>One identity, two authorization surfaces.</h1>
        <p class="hero-copy">
          Nuxt uses local <code>UI:*</code> permissions for presentation. Spring independently
          authorizes every API and administration request.
        </p>
        <div class="hero-actions">
          <button
            v-if="authorization.status.value === 'unauthenticated'"
            class="button primary"
            @click="authorization.login()"
          >
            Sign in with Keycloak
          </button>
          <button class="button secondary" @click="authorization.refreshPermissions()">
            Refresh permissions
          </button>
        </div>
      </div>
      <aside class="connection-card">
        <span class="connection-dot" />
        <div>
          <strong>Backend connection</strong>
          <p>{{ backend?.message ?? 'Checking Spring backend…' }}</p>
        </div>
      </aside>
    </section>

    <section class="permission-overview">
      <div>
        <p class="eyebrow">CURRENT SESSION</p>
        <h2>{{ profile ? `Signed in as ${profile.user}` : 'No authenticated profile' }}</h2>
        <p>Status: <strong>{{ authorization.status.value }}</strong> · entitlement version
          {{ authorization.entitlementVersion.value }}</p>
      </div>
      <div class="permission-chips" aria-label="Effective UI permissions">
        <span v-for="permission in authorization.permissions.value" :key="permission">
          {{ permission }}
        </span>
        <span v-if="authorization.permissions.value.length === 0" class="muted-chip">
          No UI permissions
        </span>
      </div>
    </section>

    <section class="showcase-grid">
      <Authorized permission="UI:DEMO_PAGE_1">
        <article class="feature-card mint">
          <span class="card-number">01</span>
          <h2>Viewer content</h2>
          <p>Rendered by <code>&lt;Authorized&gt;</code> for users with UI:DEMO_PAGE_1.</p>
          <NuxtLink class="inline-link" to="/page-one">Open protected page →</NuxtLink>
        </article>
        <template #fallback>
          <article class="feature-card locked">
            <span class="card-number">01</span>
            <h2>Viewer content is hidden</h2>
            <p>The component fallback renders while the required permission is unavailable.</p>
          </article>
        </template>
      </Authorized>

      <article v-authorization="'UI:DEMO_PAGE_2'" class="feature-card violet">
        <span class="card-number">02</span>
        <h2>Manager content</h2>
        <p>This entire card is controlled by the <code>v-authorization</code> directive.</p>
        <NuxtLink class="inline-link" to="/page-two">Open manager page →</NuxtLink>
      </article>

      <Authorized permission="UI:AUTHORIZATION_ADMIN">
        <article class="feature-card amber">
          <span class="card-number">03</span>
          <h2>Framework administration</h2>
          <p>The packaged React UI is served by Spring and proxied through the Nuxt origin.</p>
          <a class="inline-link" href="/authorization-admin/">Open admin UI →</a>
        </article>
      </Authorized>
    </section>

    <section v-if="authorization.status.value === 'ready'" class="employee-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">AUTHORIZED FETCH + CSRF</p>
          <h2>Employee editor</h2>
        </div>
        <span>{{ access.employeeEdit ? 'Edit controls granted' : 'Read-only access' }}</span>
      </div>
      <p v-if="requestError" class="error-message">{{ requestError }}</p>
      <div v-if="employees.length" class="employee-list">
        <article v-for="employee in employees" :key="employee.id" class="employee-row">
          <div>
            <strong>{{ employee.name }}</strong>
            <span>{{ employee.department }}</span>
          </div>
          <label>
            <span class="sr-only">Edit {{ employee.name }}</span>
            <input v-model="drafts[employee.id]" v-authorization.disable="'UI:EMPLOYEE_EDIT'">
          </label>
          <button
            class="button primary small"
            v-authorization.disable="'UI:EMPLOYEE_EDIT'"
            :disabled="saving === employee.id"
            @click="save(employee)"
          >
            {{ saving === employee.id ? 'Saving…' : 'Save' }}
          </button>
        </article>
      </div>
      <p v-else class="empty-state">No employee data is available for this identity.</p>
    </section>
  </main>
</template>
