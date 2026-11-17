import { ref } from 'vue'

type Theme = 'light' | 'dark'
const KEY = 'nexora-theme'

const isDark = ref(typeof document !== 'undefined' && document.documentElement.classList.contains('dark'))

/** Tema global: o script do index.html aplica o valor salvo antes da pintura; aqui só alternamos. */
export function useTheme() {
  function set(theme: Theme) {
    isDark.value = theme === 'dark'
    document.documentElement.classList.toggle('dark', isDark.value)
    try {
      localStorage.setItem(KEY, theme)
    } catch {
      // sem persistência: vale até recarregar
    }
  }
  const toggle = () => set(isDark.value ? 'light' : 'dark')
  return { isDark, toggle }
}
