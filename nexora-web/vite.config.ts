import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vitest/config'

// No Compose o backend é alcançado pelo nome do serviço; o navegador só fala com o Vite,
// então não há CORS e nenhuma URL do backend vai para o bundle.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://backend:8080'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // Bind mounts no Docker/WSL2 nem sempre emitem eventos de arquivo.
    watch: { usePolling: process.env.CHOKIDAR_USEPOLLING === 'true' },
    proxy: { '/api': { target: apiTarget, changeOrigin: true } },
  },
  test: { environment: 'jsdom', globals: false, restoreMocks: true },
})
