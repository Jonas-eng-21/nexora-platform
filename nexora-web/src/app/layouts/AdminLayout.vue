<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const LINKS = [
  { to: '/admin', label: 'Início', exact: true },
  { to: '/admin/universidades', label: 'Universidades', exact: false },
  { to: '/admin/usuarios', label: 'Contas', exact: false },
  { to: '/admin/vinculos', label: 'Vínculos', exact: false },
  { to: '/admin/vagas', label: 'Vagas', exact: false },
  { to: '/admin/auditoria', label: 'Auditoria', exact: false },
]
</script>

<template>
  <div class="space-y-6">
    <nav aria-label="Área do administrador">
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
