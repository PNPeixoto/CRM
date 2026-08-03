/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react(), tailwindcss()],

  build: {
    // Explícito, e não herdado do padrão: source map publicado como ativo
    // estático entrega o código original, os nomes internos e os comentários
    // a qualquer visitante. O padrão do Vite já é `false`, mas um padrão pode
    // mudar de versão sem ninguém notar — aqui a decisão fica registrada e o
    // teste de contrato a verifica.
    sourcemap: false,
  },

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

  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: { url: 'http://localhost/' },
    },
    setupFiles: ['./src/test/setup.ts'],
    allowOnly: false,
    clearMocks: true,
    restoreMocks: true,
    unstubEnvs: true,
    unstubGlobals: true,
    testTimeout: 5000,
    hookTimeout: 10000,
    expect: { requireAssertions: true },
    sequence: {
      hooks: 'list',
      setupFiles: 'list',
    },
  },
});
