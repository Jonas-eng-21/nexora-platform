import { nextTick } from 'vue'

/** Leva o foco ao primeiro campo inválido do formulário para teclado e leitores de tela. */
export async function focusFirstInvalid(form: HTMLFormElement | null): Promise<void> {
  await nextTick()
  form?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
}
