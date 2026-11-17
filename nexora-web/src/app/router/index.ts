import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSessionStore } from '@/features/authentication/stores/session'
import type { UserRole } from '@/features/authentication/types'

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean
    requiresAuth?: boolean
    role?: UserRole
    title?: string
  }
}

const AccountHomeView = () => import('@/features/authentication/views/AccountHomeView.vue')

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'root', component: { render: () => null }, meta: { requiresAuth: true } },
  {
    path: '/',
    component: () => import('@/app/layouts/AuthLayout.vue'),
    children: [
      { path: 'login', name: 'login', component: () => import('@/features/authentication/views/LoginView.vue'), meta: { guestOnly: true, title: 'Entrar' } },
      { path: 'cadastro', name: 'register', component: () => import('@/features/authentication/views/RegisterView.vue'), meta: { guestOnly: true, title: 'Criar conta' } },
    ],
  },
  {
    path: '/',
    component: () => import('@/app/layouts/AppShell.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: 'estudante', name: 'student-home', component: AccountHomeView, meta: { requiresAuth: true, role: 'STUDENT', title: 'Área do estudante' } },
      { path: 'empresa', name: 'company-home', component: AccountHomeView, meta: { requiresAuth: true, role: 'COMPANY', title: 'Área da empresa' } },
      { path: 'universidade', name: 'university-home', component: AccountHomeView, meta: { requiresAuth: true, role: 'UNIVERSITY', title: 'Área da universidade' } },
      { path: 'admin', name: 'admin-home', component: AccountHomeView, meta: { requiresAuth: true, role: 'ADMIN', title: 'Área do administrador' } },
      { path: 'acesso-negado', name: 'forbidden', component: () => import('@/app/views/ForbiddenView.vue'), meta: { requiresAuth: true, title: 'Acesso negado' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/app/views/NotFoundView.vue'), meta: { title: 'Página não encontrada' } },
]

export function createAppRouter() {
  const router = createRouter({ history: createWebHistory(), routes })

  router.beforeEach(async (to) => {
    const session = useSessionStore()
    if (!session.initialized) await session.restore()

    if (to.name === 'root') {
      return session.isAuthenticated ? session.homePath : { name: 'login' }
    }
    if (to.meta.guestOnly && session.isAuthenticated) return session.homePath
    if (to.meta.requiresAuth && !session.isAuthenticated) {
      const reason = session.restoreError ? 'unavailable' : undefined
      return { name: 'login', query: reason ? { reason } : undefined }
    }
    // A autorização real é do backend; aqui só evitamos expor telas de outro papel.
    if (to.meta.role && to.meta.role !== session.role) return { name: 'forbidden' }
    return true
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · Nexora` : 'Nexora'
  })

  return router
}
