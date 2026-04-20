import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import path from 'path' // 补充引入 path，解决下面的红线

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3002,
    strictPort: true,
   
    proxy: {
      '/api': {
        target: 'http://192.168.212.251:13715',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/athena')
      }
    }
  }
})