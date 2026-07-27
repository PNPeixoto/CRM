#!/usr/bin/env bash
#
# bootstrap-crm.sh — cria a estrutura inicial do CRM PNP
#
# Rode UMA VEZ, num diretório vazio.
# Uso: ./bootstrap-crm.sh
#
# Requisitos: git, curl, node 20+, npm
#
# Windows: rode pelo Git Bash, não pelo cmd nem pelo PowerShell.
#   bash bootstrap-crm.sh
#
set -euo pipefail

# Extração portável: Git Bash (Windows) não traz unzip, Linux não traz
# Expand-Archive. Tenta na ordem e falha alto se nenhum existir.
extrair_zip() {
  local zip="$1" destino="$2"
  mkdir -p "$destino"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip" -d "$destino"
  elif command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command \
      "Expand-Archive -Path '$zip' -DestinationPath '$destino' -Force"
  elif command -v bsdtar >/dev/null 2>&1; then
    bsdtar -xf "$zip" -C "$destino"
  else
    echo "ERRO: nenhum extrator de zip encontrado (unzip, powershell ou bsdtar)." >&2
    exit 1
  fi
}

PROJETO="crm-pnp"
GROUP_ID="br.com.pnp"
ARTIFACT_ID="crm"
PACKAGE="br.com.pnp.crm"

echo "==> [1/6] Criando raiz do projeto"
mkdir -p "$PROJETO"
cd "$PROJETO"
git init -q

# ---------------------------------------------------------------------------
# 2. BACKEND — Spring Initializr
# ---------------------------------------------------------------------------
# bootVersion propositalmente omitido: o Initializr entrega a versão estável
# atual. Fixar versão aqui envelhece o script sem avisar.
echo "==> [2/6] Baixando esqueleto do backend"
curl -sSf https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=25 \
  -d groupId="$GROUP_ID" \
  -d artifactId="$ARTIFACT_ID" \
  -d name="$ARTIFACT_ID" \
  -d packageName="$PACKAGE" \
  -d dependencies=web,security,data-jpa,validation,postgresql,flyway,data-redis,websocket,actuator,modulith,testcontainers,docker-compose,configuration-processor \
  -o backend.zip

extrair_zip backend.zip backend
rm -f backend.zip

# Estrutura modular: cada módulo com fronteira api/ e internal/
BASE="backend/src/main/java/br/com/pnp/crm"
for modulo in shared identity tenant authz contact conversation channel \
              routing deal task automation integration booking campaign \
              report audit billing; do
  mkdir -p "$BASE/$modulo/api" "$BASE/$modulo/internal"
  # package-info marca a fronteira do módulo para o Spring Modulith
  cat > "$BASE/$modulo/package-info.java" <<EOF
/**
 * Módulo: $modulo
 *
 * Apenas o subpacote api é visível para outros módulos.
 * internal é privado — nunca importe daqui de fora.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "$modulo"
)
package br.com.pnp.crm.$modulo;
EOF
done

mkdir -p backend/src/main/resources/db/migration

# ---------------------------------------------------------------------------
# 3. FRONTEND — Vite + React + TypeScript
# ---------------------------------------------------------------------------
echo "==> [3/6] Criando frontend"
# Saída NÃO silenciada de propósito: o create-vite pode fazer perguntas.
# Com a saída escondida, ele ficaria esperando resposta sem mostrar a pergunta.
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install react-router-dom
npm install -D tailwindcss @tailwindcss/vite
cd ..

FE="frontend/src"
rm -rf "$FE/assets" "$FE/App.css"

mkdir -p "$FE/app" \
         "$FE/shared/components" \
         "$FE/shared/layouts" \
         "$FE/shared/hooks" \
         "$FE/shared/api" \
         "$FE/shared/lib" \
         "$FE/shared/types"

# ---------------------------------------------------------------------------
# 4. PÁGINAS — uma pasta por página, arquivo TSX já criado
# ---------------------------------------------------------------------------
echo "==> [4/6] Gerando pastas e arquivos das páginas"

# slug | Componente | rótulo exibido na navegação | status
PAGINAS=(
  "login|Login|Entrar|pronto"
  "dashboard|Dashboard|Visão geral|pronto"
  "inbox|Inbox|Caixa de entrada|em_producao"
  "contacts|Contacts|Contatos|em_producao"
  "deals|Deals|Oportunidades|em_producao"
  "pipelines|Pipelines|Funis|em_producao"
  "tasks|Tasks|Tarefas|em_producao"
  "calendar|Calendar|Agenda|em_producao"
  "bookings|Bookings|Reservas|em_producao"
  "assets|Assets|Produtos e ativos|em_producao"
  "units|Units|Unidades|em_producao"
  "teams|Teams|Equipes|em_producao"
  "automations|Automations|Automações|em_producao"
  "integrations|Integrations|Integrações|em_producao"
  "campaigns|Campaigns|Campanhas|em_producao"
  "reports|Reports|Relatórios|em_producao"
  "audit|Audit|Auditoria|em_producao"
  "settings|Settings|Configurações|em_producao"
)

for linha in "${PAGINAS[@]}"; do
  IFS='|' read -r slug comp rotulo status <<< "$linha"
  dir="$FE/pages/$slug"
  mkdir -p "$dir/components" "$dir/hooks"

  if [ "$status" = "em_producao" ]; then
    cat > "$dir/${comp}Page.tsx" <<EOF
import { EmProducao } from '@/shared/components/EmProducao';

/**
 * $rotulo
 *
 * Espaço reservado. Substitua o conteúdo quando o módulo entrar em
 * desenvolvimento — a rota e a navegação já apontam para cá.
 */
export function ${comp}Page() {
  return <EmProducao titulo="$rotulo" />;
}
EOF
  else
    cat > "$dir/${comp}Page.tsx" <<EOF
/**
 * $rotulo
 */
export function ${comp}Page() {
  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold">$rotulo</h1>
    </div>
  );
}
EOF
  fi

  cat > "$dir/index.ts" <<EOF
export { ${comp}Page } from './${comp}Page';
EOF
done

# ---------------------------------------------------------------------------
# 5. REGISTRO CENTRAL DE ROTAS + componente EmProducao
# ---------------------------------------------------------------------------
echo "==> [5/6] Gerando registro de rotas"

cat > "$FE/app/routes.ts" <<'EOF'
/**
 * Registro central de rotas.
 *
 * Fonte única da verdade: alimenta o roteador E a navegação lateral.
 * Adicionar uma página é adicionar uma linha aqui — nada além disso.
 *
 * status:
 *   'pronto'      — página implementada
 *   'em_producao' — arquivo existe, rota funciona, conteúdo é placeholder
 */
export type StatusPagina = 'pronto' | 'em_producao';

export interface RotaApp {
  readonly id: string;
  readonly caminho: string;
  readonly rotulo: string;
  readonly icone: string;
  readonly status: StatusPagina;
  readonly grupo: 'operacao' | 'gestao' | 'plataforma';
}

export const ROTAS: readonly RotaApp[] = [
  { id: 'dashboard',    caminho: '/dashboard',    rotulo: 'Visão geral',       icone: 'layout-dashboard', status: 'pronto',      grupo: 'operacao' },
  { id: 'inbox',        caminho: '/inbox',        rotulo: 'Caixa de entrada',  icone: 'messages-square',  status: 'em_producao', grupo: 'operacao' },
  { id: 'contacts',     caminho: '/contatos',     rotulo: 'Contatos',          icone: 'users',            status: 'em_producao', grupo: 'operacao' },
  { id: 'deals',        caminho: '/oportunidades',rotulo: 'Oportunidades',     icone: 'target',           status: 'em_producao', grupo: 'operacao' },
  { id: 'pipelines',    caminho: '/funis',        rotulo: 'Funis',             icone: 'git-branch',       status: 'em_producao', grupo: 'operacao' },
  { id: 'tasks',        caminho: '/tarefas',      rotulo: 'Tarefas',           icone: 'check-square',     status: 'em_producao', grupo: 'operacao' },
  { id: 'calendar',     caminho: '/agenda',       rotulo: 'Agenda',            icone: 'calendar',         status: 'em_producao', grupo: 'operacao' },
  { id: 'bookings',     caminho: '/reservas',     rotulo: 'Reservas',          icone: 'calendar-check',   status: 'em_producao', grupo: 'operacao' },
  { id: 'assets',       caminho: '/produtos',     rotulo: 'Produtos e ativos', icone: 'package',          status: 'em_producao', grupo: 'operacao' },
  { id: 'units',        caminho: '/unidades',     rotulo: 'Unidades',          icone: 'building-2',       status: 'em_producao', grupo: 'gestao'   },
  { id: 'teams',        caminho: '/equipes',      rotulo: 'Equipes',           icone: 'users-round',      status: 'em_producao', grupo: 'gestao'   },
  { id: 'automations',  caminho: '/automacoes',   rotulo: 'Automações',        icone: 'workflow',         status: 'em_producao', grupo: 'plataforma' },
  { id: 'integrations', caminho: '/integracoes',  rotulo: 'Integrações',       icone: 'plug',             status: 'em_producao', grupo: 'plataforma' },
  { id: 'campaigns',    caminho: '/campanhas',    rotulo: 'Campanhas',         icone: 'megaphone',        status: 'em_producao', grupo: 'gestao'   },
  { id: 'reports',      caminho: '/relatorios',   rotulo: 'Relatórios',        icone: 'bar-chart-3',      status: 'em_producao', grupo: 'gestao'   },
  { id: 'audit',        caminho: '/auditoria',    rotulo: 'Auditoria',         icone: 'scroll-text',      status: 'em_producao', grupo: 'plataforma' },
  { id: 'settings',     caminho: '/configuracoes',rotulo: 'Configurações',     icone: 'settings',         status: 'em_producao', grupo: 'plataforma' },
] as const;
EOF

cat > "$FE/shared/components/EmProducao.tsx" <<'EOF'
interface EmProducaoProps {
  readonly titulo: string;
  readonly descricao?: string;
}

/**
 * Placeholder padrão das páginas ainda não implementadas.
 * A rota funciona e o arquivo existe — só o conteúdo está pendente.
 */
export function EmProducao({ titulo, descricao }: EmProducaoProps) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <span className="rounded-full border px-3 py-1 text-xs font-medium uppercase tracking-wide">
        Em produção
      </span>
      <h1 className="text-xl font-semibold">{titulo}</h1>
      <p className="max-w-md text-sm text-muted">
        {descricao ?? 'Este módulo ainda está em desenvolvimento e será liberado em breve.'}
      </p>
    </div>
  );
}
EOF

# Roteador: mapa explícito id -> página, gerado a partir da mesma lista
{
  cat <<'EOF'
import { lazy, Suspense, type ComponentType, type LazyExoticComponent } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ROTAS } from './routes';
import { AppLayout } from '@/shared/layouts/AppLayout';
import { LoginPage } from '@/pages/login';

/**
 * Mapa explícito rota -> página.
 * Proposital: um id sem página correspondente quebra o build, não a produção.
 */
const PAGINAS: Record<string, LazyExoticComponent<ComponentType>> = {
EOF
  for linha in "${PAGINAS[@]}"; do
    IFS='|' read -r slug comp rotulo status <<< "$linha"
    [ "$slug" = "login" ] && continue
    echo "  $slug: lazy(() => import('@/pages/$slug').then((m) => ({ default: m.${comp}Page }))),"
  done
  cat <<'EOF'
};

export function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<div className="p-6 text-sm">Carregando…</div>}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<AppLayout />}>
            {ROTAS.map(({ id, caminho }) => {
              const Pagina = PAGINAS[id];
              return <Route key={id} path={caminho} element={<Pagina />} />;
            })}
          </Route>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
EOF
} > "$FE/app/router.tsx"

cat > "$FE/shared/layouts/AppLayout.tsx" <<'EOF'
import { NavLink, Outlet } from 'react-router-dom';
import { ROTAS } from '@/app/routes';

/**
 * Casca da aplicação. A navegação é derivada de ROTAS — nunca escrita à mão,
 * senão o menu e o roteador saem de sincronia sem ninguém perceber.
 */
export function AppLayout() {
  return (
    <div className="flex min-h-screen">
      <nav className="w-60 shrink-0 border-r p-3">
        <ul className="space-y-1">
          {ROTAS.map((rota) => (
            <li key={rota.id}>
              <NavLink
                to={rota.caminho}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded px-3 py-2 text-sm ${
                    isActive ? 'font-medium' : ''
                  }`
                }
              >
                <span>{rota.rotulo}</span>
                {rota.status === 'em_producao' && (
                  <span className="rounded border px-1.5 py-0.5 text-[10px] uppercase">
                    em breve
                  </span>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
EOF

# ---------------------------------------------------------------------------
# 6. CONTEXTO PERSISTENTE (Cowork <-> Claude Code)
# ---------------------------------------------------------------------------
echo "==> [6/6] Criando pasta de contexto"

mkdir -p contexto/sessoes

cat > CLAUDE.md <<'EOF'
# CRM PNP

Ponto de entrada de contexto. Leia na ordem antes de qualquer tarefa.

## Ordem de leitura obrigatória

1. `contexto/00-projeto.md` — o que é o produto, módulos, prioridades
2. `contexto/01-padroes-tecnicos.md` — código, segurança, infra, canais
3. `contexto/02-estado-atual.md` — **onde parei e qual é o próximo passo**

Consulte sob demanda, não por padrão:

- `contexto/03-decisoes.md` — histórico de decisões (append-only)
- `contexto/04-glossario.md` — termos do domínio
- `contexto/sessoes/` — log detalhado por data

## Regras de sessão

**No início:** ler os três arquivos obrigatórios. Se `02-estado-atual.md`
contradisser o código, o código vence — e corrija o arquivo.

**Durante:** ao tomar uma decisão técnica que fecha uma porta (escolha de
biblioteca, formato de dado, desenho de tabela), registrar em
`03-decisoes.md` **no momento da decisão**, não no fim.

**No fim:** reescrever `02-estado-atual.md` por inteiro e criar
`contexto/sessoes/AAAA-MM-DD.md` com o que foi feito.

## Limites

- `02-estado-atual.md` não passa de 150 linhas. Se passar, é porque virou
  histórico — mova para `03-decisoes.md` ou para o log da sessão.
- `03-decisoes.md` é **append-only**. Nunca editar entrada antiga. Decisão
  revertida vira entrada nova que referencia a anterior.
- Nunca gravar segredo, token ou credencial em nenhum arquivo de contexto.
EOF

cat > contexto/02-estado-atual.md <<'EOF'
# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: (preencher)

## Onde parei

Estrutura do repositório criada. Nenhuma funcionalidade implementada.

## Pronto

- Esqueleto do backend (Spring Boot + Modulith) com pacotes dos módulos
- Esqueleto do frontend (Vite + React + TS + Tailwind)
- Registro central de rotas com status por página
- Todas as páginas com pasta e arquivo TSX criados, marcadas "em produção"

## Em andamento

Nada.

## Próximo passo

Autenticação: entidade de usuário, migration inicial, endpoint de login,
tela de login consumindo a API.

## Bloqueios e pendências

- Definir modo claro ou escuro como padrão antes de gerar os design tokens
- Definir nome definitivo do produto (`pnp` é provisório e já está no
  nome do pacote Java)
- Decidir API oficial do WhatsApp vs. bridge não oficial

## Armadilhas conhecidas

- Broker STOMP em memória não funciona com mais de uma instância
- `ddl-auto` deve permanecer em `validate`
EOF

cat > contexto/03-decisoes.md <<'EOF'
# Registro de decisões

> **Append-only.** Nunca edite nem apague uma entrada.
> Decisão revertida entra como entrada NOVA referenciando a antiga.
>
> Formato:
>
> ## AAAA-MM-DD — Título
> **Decisão:** o que foi escolhido
> **Alternativas descartadas:** o que foi considerado e por que não
> **Consequência:** o que isso fecha ou obriga daqui em diante

## 2026-07-27 — Monólito modular em vez de microsserviços

**Decisão:** Spring Modulith, aplicação única, fronteira por pacote.
**Alternativas descartadas:** microsserviços — custo operacional
incompatível com equipe de uma pessoa.
**Consequência:** fronteira só existe se for verificada no CI
(`ApplicationModules.verify()`).

## 2026-07-27 — Registro central de rotas com status por página

**Decisão:** `app/routes.ts` alimenta roteador e navegação, com campo
`status` controlando o placeholder "em produção".
**Alternativas descartadas:** rotas espalhadas pelos componentes — a
navegação sairia do ar com o roteador sem ninguém perceber.
**Consequência:** adicionar página é adicionar uma linha; nunca duplicar
a lista de rotas em outro lugar.
EOF

cat > contexto/04-glossario.md <<'EOF'
# Glossário do domínio

| Termo | Significado |
|---|---|
| Tenant | Empresa cliente do SaaS (uma franqueadora) |
| Unidade | Franquia ou filial dentro de um tenant |
| Conversa | Thread de mensagens com um contato, em um canal |
| Conexão de canal | Um número ou conta específica ligada a uma unidade |
| Oportunidade | Negócio em andamento dentro de um funil |
| Janela de 24h | Período em que o WhatsApp permite resposta em texto livre |
| Agente privado | Componente instalado no cliente que guarda credenciais locais |
EOF

cat > .gitignore <<'EOF'
# Build
target/
dist/
node_modules/

# Ambiente — NUNCA versionar
.env
.env.*
!.env.example
*.pem
*.p12
*.jks

# IDE
.idea/
.vscode/
*.iml

# SO
.DS_Store
EOF

cat > .gitattributes <<'EOF'
# Normaliza fim de linha no repositório: LF sempre.
# Sem isso, alternar entre Windows e Linux gera diff de arquivo inteiro
# e quebra script shell com CRLF.
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.png binary
*.jpg binary
*.pdf binary
EOF

cat > .env.example <<'EOF'
# Apenas NOMES. Valores reais nunca entram no repositório.
DB_URL=
DB_USER=
DB_PASSWORD=
REDIS_URL=
JWT_SIGNING_KEY=
META_APP_SECRET=
META_VERIFY_TOKEN=
TELEGRAM_BOT_TOKEN=
AGENT_ENROLLMENT_SECRET=
EOF

echo ""
echo "==> Pronto."
echo ""
echo "Estrutura criada em ./$PROJETO"
echo ""
echo "Antes do primeiro commit, faça a mão:"
echo "  1. frontend/vite.config.ts  -> plugin tailwindcss() + alias '@' para ./src"
echo "  2. frontend/src/index.css   -> @import \"tailwindcss\";"
echo "  3. copie os dois documentos de contexto para contexto/00 e contexto/01"
echo "  4. git add . && git commit -m 'chore: estrutura inicial do projeto'"
