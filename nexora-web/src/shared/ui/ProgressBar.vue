<script setup lang="ts">
const props = withDefaults(defineProps<{ value: number; max?: number; label: string }>(), { max: 100 })
const percent = () => Math.min(100, Math.max(0, (props.value / props.max) * 100))
</script>

<template>
  <div
    role="progressbar"
    :aria-label="label"
    :aria-valuenow="value"
    aria-valuemin="0"
    :aria-valuemax="max"
    class="h-2 w-full overflow-hidden rounded-full bg-line"
  >
    <div class="h-full rounded-full bg-primary" :style="{ width: `${percent()}%` }" />
  </div>
</template>
