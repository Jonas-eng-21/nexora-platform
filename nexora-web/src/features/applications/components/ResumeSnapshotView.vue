<script setup lang="ts">
import { formatYearMonth } from '@/shared/format'
import type { ResumeSnapshot } from '../types'

defineProps<{ snapshot: ResumeSnapshot }>()
</script>

<template>
  <div class="space-y-5 break-words text-sm">
    <header>
      <h4 class="text-base font-semibold">{{ snapshot.profile.fullName }}</h4>
      <p class="text-muted">{{ [snapshot.profile.course, snapshot.profile.institution, snapshot.profile.location].filter(Boolean).join(' · ') }}</p>
      <p v-if="snapshot.profile.summary" class="mt-2 whitespace-pre-line">{{ snapshot.profile.summary }}</p>
    </header>

    <section v-if="snapshot.skills.length">
      <h5 class="mb-1 font-semibold">Competências técnicas</h5>
      <p>{{ snapshot.skills.map((s) => s.name).join(', ') }}</p>
    </section>

    <section v-if="snapshot.resume.projects.length">
      <h5 class="mb-1 font-semibold">Projetos</h5>
      <ul class="space-y-2">
        <li v-for="(p, i) in snapshot.resume.projects" :key="i">
          <p class="font-medium">{{ p.name }}</p>
          <p v-if="p.description" class="whitespace-pre-line text-muted">{{ p.description }}</p>
          <a v-if="p.url" :href="p.url" target="_blank" rel="noopener noreferrer" class="text-primary underline underline-offset-4">{{ p.url }}</a>
        </li>
      </ul>
    </section>

    <section v-if="snapshot.resume.education.length">
      <h5 class="mb-1 font-semibold">Formação</h5>
      <ul class="space-y-1">
        <li v-for="(e, i) in snapshot.resume.education" :key="i">
          {{ e.course }}<span v-if="e.degree"> ({{ e.degree }})</span> — {{ e.institution }}, {{ e.startYear }}<template v-if="e.endYear"> a {{ e.endYear }}</template>
        </li>
      </ul>
    </section>

    <section v-if="snapshot.resume.courses.length">
      <h5 class="mb-1 font-semibold">Cursos e certificações</h5>
      <ul class="space-y-1">
        <li v-for="(c, i) in snapshot.resume.courses" :key="i">{{ c.name }}<template v-if="c.institution"> — {{ c.institution }}</template><template v-if="c.year">, {{ c.year }}</template></li>
      </ul>
    </section>

    <section v-if="snapshot.resume.experiences.length">
      <h5 class="mb-1 font-semibold">Experiência profissional</h5>
      <ul class="space-y-2">
        <li v-for="(e, i) in snapshot.resume.experiences" :key="i">
          <p class="font-medium">{{ e.role }} — {{ e.company }}</p>
          <p class="text-muted">{{ formatYearMonth(e.startDate) }} a {{ e.endDate ? formatYearMonth(e.endDate) : 'atual' }}</p>
          <p v-if="e.description" class="whitespace-pre-line">{{ e.description }}</p>
        </li>
      </ul>
    </section>

    <section v-if="snapshot.resume.softSkills.length || snapshot.resume.interests.length">
      <h5 class="mb-1 font-semibold">Soft skills e interesses</h5>
      <p v-if="snapshot.resume.softSkills.length">Soft skills: {{ snapshot.resume.softSkills.join(', ') }}.</p>
      <p v-if="snapshot.resume.interests.length">Interesses: {{ snapshot.resume.interests.join(', ') }}.</p>
    </section>

    <section v-if="snapshot.resume.links.github || snapshot.resume.links.linkedin || snapshot.resume.links.portfolio">
      <h5 class="mb-1 font-semibold">Links</h5>
      <ul class="space-y-1">
        <li v-for="(url, name) in snapshot.resume.links" :key="name">
          <a v-if="url" :href="url" target="_blank" rel="noopener noreferrer" class="text-primary underline underline-offset-4">{{ name === 'github' ? 'GitHub' : name === 'linkedin' ? 'LinkedIn' : 'Portfólio' }}</a>
        </li>
      </ul>
    </section>
  </div>
</template>
