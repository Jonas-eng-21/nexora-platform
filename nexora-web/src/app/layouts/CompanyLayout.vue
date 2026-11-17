<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const LINKS = [
  { to: '/empresa', label: 'Início', exact: true },
  { to: '/empresa/vagas', label: 'Vagas', exact: false },
  { to: '/empresa/perfil', label: 'Perfil da empresa', exact: false },
]
</script>

<template>
  <div class="space-y-6">
    <nav aria-label="Área da empresa">
      <ul class="flex flex-wrap gap-1">
        <li v-for="link in LINKS" :key="link.to">
          <RouterLink
            :to="link.to"
            :exact-active-class="link.exact ? 'bg-primary-soft font-semibold' : ''"
            :active-class="link.exact ? '' : 'bg-primary-soft font-semibold'"
            class="inline-flex min-h-11 items-center rounded-control px-3 text-sm hover:bg-primary-soft"
          >
            {{ link.label }}
          </RouterLink>
        </li>
      </ul>
    </nav>
    <RouterView />
  </div>
</template>
