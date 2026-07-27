#!/usr/bin/env bash
#
# configurar.sh — aplica as configurações que faltavam para o projeto rodar.
#
# Rode DENTRO da pasta crm-pnp, depois do bootstrap:
#   cd crm-pnp
#   bash configurar.sh
#
set -euo pipefail

if [ ! -d frontend ] || [ ! -d backend ]; then
  echo "ERRO: rode este script de dentro da pasta crm-pnp" >&2
  exit 1
fi

echo "==> [1/5] vite.config.ts"
cat > frontend/vite.config.ts <<'EOF'
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
    port: 5173,
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
EOF

echo "==> [2/5] tsconfig e CSS"
# O Vite resolve o alias no bundle; o TypeScript precisa saber dele
# separadamente, senão 'npm run build' falha mesmo com 'npm run dev' passando.
if ! grep -q '"@/\*"' frontend/tsconfig.app.json; then
  sed -i.bak 's|"compilerOptions": {|"compilerOptions": {\n    "baseUrl": ".",\n    "paths": { "@/*": ["./src/*"] },|' frontend/tsconfig.app.json
  rm -f frontend/tsconfig.app.json.bak
fi

cat > frontend/src/index.css <<'EOF'
@import "tailwindcss";
EOF

echo "==> [3/5] main.tsx"
rm -f frontend/src/App.tsx
cat > frontend/src/main.tsx <<'EOF'
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AppRouter } from './app/router';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppRouter />
  </StrictMode>,
);
EOF

echo "==> [4/5] docker-compose.yml"
cat > docker-compose.yml <<'EOF'
# Ambiente de DESENVOLVIMENTO apenas.
services:
  postgres:
    image: postgres:17-alpine
    container_name: crm-postgres
    environment:
      POSTGRES_DB: crm
      POSTGRES_USER: crm
      # TESTE — TROCAR ANTES DE PRODUÇÃO.
      # Em produção vem de variável de ambiente, nunca de arquivo versionado.
      POSTGRES_PASSWORD: crm_dev_local
    ports:
      # 127.0.0.1 é proposital: o Docker publica em 0.0.0.0 por padrão e
      # ignora o firewall do sistema. Sem isso, seu banco fica exposto na
      # rede em que você estiver.
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U crm -d crm"]
      interval: 5s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: crm-redis
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  postgres_data:
EOF

echo "==> [5/5] application.yml"
cat > backend/src/main/resources/application.yml <<'EOF'
spring:
  application:
    name: crm

  datasource:
    # Sintaxe ${VAR:padrao}: usa a variável de ambiente quando existir.
    # Em produção, nenhuma dessas terá valor padrão no arquivo.
    url: ${DB_URL:jdbc:postgresql://localhost:5432/crm}
    username: ${DB_USER:crm}
    password: ${DB_PASSWORD:crm_dev_local}   # TESTE — TROCAR ANTES DE PRODUÇÃO

  jpa:
    hibernate:
      # validate: o Hibernate confere se o banco bate com as entidades e
      # falha se não bater. Nunca 'update' — ele altera o schema sozinho,
      # sem histórico, e o que roda em produção deixa de ser reproduzível.
      ddl-auto: validate
    # Fecha a sessão ao sair da camada de serviço. Evita consulta preguiçosa
    # disparada na serialização da resposta, que é onde nascem os erros de
    # lazy initialization e boa parte dos N+1.
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration

  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}

server:
  port: 8080
  # Faz a aplicação respeitar X-Forwarded-* quando houver proxy na frente.
  forward-headers-strategy: framework

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true

logging:
  level:
    br.com.pnp.crm: DEBUG
EOF

rm -f backend/src/main/resources/application.properties

echo ""
echo "==> Pronto. Para rodar:"
echo ""
echo "  Terminal 1 — banco e cache:"
echo "    docker compose up -d"
echo ""
echo "  Terminal 2 — backend:"
echo "    cd backend && ./mvnw spring-boot:run"
echo ""
echo "  Terminal 3 — frontend:"
echo "    cd frontend && npm run dev"
echo ""
echo "  Navegador: http://localhost:5173"
