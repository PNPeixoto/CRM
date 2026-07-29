import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    // Permite importar como '@/pages/inbox' em vez de '../../../pages/inbox'.
    // Mover um arquivo deixa de quebrar os imports dos vizinhos.
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    port: 5174,
    // Proxy do /api para o backend em desenvolvimento.
    // Assim o navegador vê tudo na mesma origem e não é preciso afrouxar
    // CORS só para desenvolver — CORS relaxado tende a vazar para produção.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
