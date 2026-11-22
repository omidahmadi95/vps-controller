import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The Spring Boot API runs on 127.0.0.1:8080; the dev server forwards /api there.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
})
