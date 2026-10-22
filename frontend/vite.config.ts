import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// 开发期后端地址，可用环境变量 VITE_DEV_PROXY 覆盖（默认本机 8000）
const devProxyTarget = process.env.VITE_DEV_PROXY || 'http://localhost:8000'
const devProxyWsTarget = devProxyTarget.replace(/^http/, 'ws')

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true,
      },
      '/ws': {
        target: devProxyWsTarget,
        ws: true,
      },
    },
  },
})
