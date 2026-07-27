import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

const configuredBackendTarget = process.env.SOLONCLAW_SERVER_URL || ''
const backendTarget = configuredBackendTarget || 'http://127.0.0.1:8080'
// Monaco 0.55 内置的 DOMPurify 3.2.7 存在已知 XSS 漏洞，统一改用锁文件中的安全版本。
const secureDomPurifyModule = resolve(__dirname, 'node_modules/dompurify/dist/purify.es.mjs')

export default defineConfig({
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify('0.0.0'),
    __SOLONCLAW_DEV_SERVER_URL__: JSON.stringify(configuredBackendTarget),
  },
  resolve: {
    alias: [
      {
        find: '@',
        replacement: resolve(__dirname, 'src'),
      },
      {
        find: /^\.\/dompurify\/dompurify\.js$/,
        replacement: secureDomPurifyModule,
      },
    ],
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 4000,
    rolldownOptions: {
      checks: {
        invalidAnnotation: false,
        pluginTimings: false,
      },
    },
  },
  optimizeDeps: {
    include: ['monaco-editor'],
  },
  server: {
    proxy: {
      '/api': backendTarget,
      '/health': backendTarget,
      '/upload': backendTarget,
    },
  },
})
