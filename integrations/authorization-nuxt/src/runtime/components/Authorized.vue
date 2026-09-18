<script setup lang="ts">
import { computed } from "vue";
import type { PermissionMatch } from "../types";

const props = withDefaults(defineProps<{
  permission?: string;
  permissions?: string[];
  match?: PermissionMatch;
}>(), {
  permission: undefined,
  permissions: undefined,
  match: "all"
});

const authorization = useAuthorization();
const required = computed(() => props.permission ? [props.permission] : props.permissions ?? []);
const allowed = computed(() => props.match === "any"
  ? authorization.canAny(required.value)
  : authorization.canAll(required.value));
</script>

<template>
  <slot v-if="allowed" :allowed="true" />
  <slot v-else name="fallback" :allowed="false" />
</template>
