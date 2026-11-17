import type { WorkModel } from './types'
import type { JobLevel } from '@/features/jobs/types'

export const WORK_MODEL_LABEL: Record<WorkModel, string> = { ON_SITE: 'Presencial', REMOTE: 'Remoto', HYBRID: 'Híbrido' }
export const WORK_MODEL_OPTIONS = (Object.keys(WORK_MODEL_LABEL) as WorkModel[]).map((value) => ({ value, label: WORK_MODEL_LABEL[value] }))

export const JOB_LEVEL_LABEL: Record<JobLevel, string> = {
  INTERNSHIP: 'Estágio', TRAINEE: 'Trainee', JUNIOR: 'Júnior', MID: 'Pleno', SENIOR: 'Sênior',
}
export const JOB_LEVEL_OPTIONS = (Object.keys(JOB_LEVEL_LABEL) as JobLevel[]).map((value) => ({ value, label: JOB_LEVEL_LABEL[value] }))
