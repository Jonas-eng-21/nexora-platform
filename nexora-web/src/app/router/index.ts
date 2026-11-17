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
      {
        path: 'estudante',
        component: () => import('@/app/layouts/StudentLayout.vue'),
        meta: { requiresAuth: true, role: 'STUDENT' },
        children: [
          { path: '', name: 'student-home', component: () => import('@/features/students/views/StudentHomeView.vue'), meta: { title: 'Início' } },
          { path: 'perfil', name: 'student-profile', component: () => import('@/features/students/views/ProfileView.vue'), meta: { title: 'Perfil' } },
          { path: 'curriculo', name: 'student-resume', component: () => import('@/features/students/views/ResumeView.vue'), meta: { title: 'Currículo' } },
          { path: 'vinculo', name: 'student-affiliation', component: () => import('@/features/universities/views/AffiliationView.vue'), meta: { title: 'Vínculo acadêmico' } },
          { path: 'vagas', name: 'student-jobs', component: () => import('@/features/jobs/views/JobsView.vue'), meta: { title: 'Vagas' } },
          { path: 'vagas/:jobId', name: 'student-job', component: () => import('@/features/jobs/views/JobDetailView.vue'), meta: { title: 'Detalhe da vaga' } },
          { path: 'candidaturas', name: 'student-applications', component: () => import('@/features/applications/views/ApplicationsView.vue'), meta: { title: 'Candidaturas' } },
        ],
      },
      {
        path: 'empresa',
        component: () => import('@/app/layouts/CompanyLayout.vue'),
        meta: { requiresAuth: true, role: 'COMPANY' },
        children: [
          { path: '', name: 'company-home', component: () => import('@/features/companies/views/CompanyHomeView.vue'), meta: { title: 'Início' } },
          { path: 'perfil', name: 'company-profile', component: () => import('@/features/companies/views/CompanyProfileView.vue'), meta: { title: 'Perfil da empresa' } },
          { path: 'vagas', name: 'company-jobs', component: () => import('@/features/jobs/views/CompanyJobsView.vue'), meta: { title: 'Vagas' } },
          { path: 'vagas/nova', name: 'company-job-new', component: () => import('@/features/jobs/views/JobFormView.vue'), meta: { title: 'Nova vaga' } },
          { path: 'vagas/:jobId/editar', name: 'company-job-edit', component: () => import('@/features/jobs/views/JobFormView.vue'), meta: { title: 'Editar vaga' } },
          { path: 'vagas/:jobId/candidaturas', name: 'company-job-applications', component: () => import('@/features/applications/views/CompanyApplicationsView.vue'), meta: { title: 'Candidaturas recebidas' } },
          { path: 'vagas/:jobId/recomendados', name: 'company-job-students', component: () => import('@/features/matching/views/RecommendedStudentsView.vue'), meta: { title: 'Estudantes recomendados' } },
        ],
      },
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
