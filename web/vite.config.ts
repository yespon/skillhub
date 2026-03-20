import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
  },
  server: {
    port: 3000,
    allowedHosts: ['skillhub.ruijie.com.cn'],
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
