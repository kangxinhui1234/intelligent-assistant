import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true
  },
  define: {
    'import.meta.env.VITE_API_BASE': JSON.stringify(process.env.VITE_API_BASE || 'http://localhost:8092')
  }
})






