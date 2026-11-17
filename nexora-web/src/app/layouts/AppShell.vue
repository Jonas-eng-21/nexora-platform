<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { useSessionStore } from '@/features/authentication/stores/session'
import { ROLE_LABEL } from '@/features/authentication/roles'
import BaseButton from '@/shared/ui/BaseButton.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import ThemeToggle from '@/shared/ui/ThemeToggle.vue'

const session = useSessionStore()
const router = useRouter()

async function logout() {
  session.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <header class="border-b border-line bg-surface">
      <div class="mx-auto flex w-full max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <BrandMark />
        <div class="flex items-center gap-2">
          <p v-if="session.user" class="hidden min-w-0 truncate text-sm text-muted sm:block">
            {{ ROLE_LABEL[session.user.role] }} · {{ session.user.email }}
          </p>
          <ThemeToggle />
          <BaseButton variant="secondary" @click="logout">Sair</BaseButton>
        </div>
      </div>
    </header>
    <main id="conteudo" class="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
      <RouterView />
    </main>
  </div>
</template>
