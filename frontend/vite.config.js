import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5176,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8091',
        changeOrigin: true,
      },
      '/local-assets': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8091',
        changeOrigin: true,
      },
    },
  },
})
