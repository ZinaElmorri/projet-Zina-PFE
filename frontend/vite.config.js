import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (/node_modules[\\/](antd|@ant-design|rc-[^\\/]+|@rc-component)[\\/]/.test(id)) return 'vendor-antd'
          return 'vendor'
        },
      },
    },
  },
})
