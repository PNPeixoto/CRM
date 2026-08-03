/** Cliente HTTP central: autenticação, cookies, cancelamento, timeout e erros. */

const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? '/api';
const TIMEOUT_PADRAO_MS = 10_000;
const MAX_TENTATIVAS_LEITURA = 3;

export type ApiErrorKind =
  | 'bad_request'
  | 'unauthenticated'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'validation'
  | 'rate_limited'
  | 'server'
  | 'network'
  | 'timeout'
  | 'cancelled'
  | 'contract';

export interface ApiFieldError {
  readonly campo: string;
  readonly mensagem: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly kind: ApiErrorKind;
  readonly codigo: string | null;
  readonly correlacaoId: string | null;
  readonly instance: string | null;
  readonly campos: readonly ApiFieldError[];
  readonly retryAfterSeconds: number | null;

  constructor(opcoes: {
    message: string;
    status: number;
    kind: ApiErrorKind;
    codigo?: string | null;
    correlacaoId?: string | null;
    instance?: string | null;
    campos?: readonly ApiFieldError[];
    retryAfterSeconds?: number | null;
  }) {
    super(opcoes.message);
    this.name = 'ApiError';
    this.status = opcoes.status;
    this.kind = opcoes.kind;
    this.codigo = opcoes.codigo ?? null;
    this.correlacaoId = opcoes.correlacaoId ?? null;
    this.instance = opcoes.instance ?? null;
    this.campos = opcoes.campos ?? [];
    this.retryAfterSeconds = opcoes.retryAfterSeconds ?? null;
  }
}

export interface RequestOptions {
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
  /** Escritas só podem repetir quando o chamador fornece uma chave estável. */
  readonly idempotencyKey?: string;
  readonly retry?: 'none' | 'idempotent-write';
}

export interface PageQuery {
  readonly page?: number;
  readonly size?: number;
  readonly sort?: readonly string[];
  readonly filters?: Readonly<Record<string, string | number | boolean | null | undefined>>;
}

export function montarConsultaPaginada(consulta: PageQuery): string {
  const page = consulta.page ?? 0;
  const size = consulta.size ?? 20;
  if (!Number.isInteger(page) || page < 0) throw new RangeError('page deve ser um inteiro não negativo.');
  if (!Number.isInteger(size) || size < 1 || size > 100) {
    throw new RangeError('size deve estar entre 1 e 100.');
  }

  const parametros = new URLSearchParams({ page: String(page), size: String(size) });
  for (const ordenacao of consulta.sort ?? []) parametros.append('sort', ordenacao);
  for (const [chave, valor] of Object.entries(consulta.filters ?? {})) {
    if (valor !== null && valor !== undefined && valor !== '') {
      parametros.append(chave, String(valor));
    }
  }
  return parametros.toString();
}

let accessToken: string | null = null;
let refreshEmAndamento: Promise<boolean> | null = null;

export function definirAccessToken(token: string | null): void {
  accessToken = token;
}

export function obterAccessToken(): string | null {
  return accessToken;
}

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

function lerCookie(nome: string): string | null {
  const encontrado = document.cookie.match(new RegExp(`(?:^|;\\s*)${nome}=([^;]*)`));
  return encontrado ? decodeURIComponent(encontrado[1]) : null;
}

function novoCorrelationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function criarSinal(opcoes: RequestOptions): {
  signal: AbortSignal;
  encerrouPorTimeout: () => boolean;
  limpar: () => void;
} {
  const controller = new AbortController();
  let timeout = false;
  const timeoutMs = opcoes.timeoutMs ?? TIMEOUT_PADRAO_MS;
  const aoCancelar = () => controller.abort(opcoes.signal?.reason);

  if (opcoes.signal?.aborted) controller.abort(opcoes.signal.reason);
  else opcoes.signal?.addEventListener('abort', aoCancelar, { once: true });

  const relogio = setTimeout(() => {
    timeout = true;
    controller.abort();
  }, timeoutMs);

  return {
    signal: controller.signal,
    encerrouPorTimeout: () => timeout,
    limpar: () => {
      clearTimeout(relogio);
      opcoes.signal?.removeEventListener('abort', aoCancelar);
    },
  };
}

async function enviar(
  metodo: Metodo,
  caminho: string,
  corpo: unknown,
  opcoes: RequestOptions,
): Promise<Response> {
  const headers: Record<string, string> = { 'X-Correlation-ID': novoCorrelationId() };
  if (corpo !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (opcoes.idempotencyKey) headers['Idempotency-Key'] = opcoes.idempotencyKey;

  if (metodo !== 'GET') {
    const xsrf = lerCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
  }

  const sinal = criarSinal(opcoes);
  try {
    return await fetch(`${API_URL}${caminho}`, {
      method: metodo,
      headers,
      credentials: 'include',
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
      signal: sinal.signal,
    });
  } catch (falha) {
    if (sinal.encerrouPorTimeout()) {
      throw new ApiError({
        message: 'A solicitação demorou demais. Tente novamente.',
        status: 0,
        kind: 'timeout',
      });
    }
    if (opcoes.signal?.aborted || (falha instanceof DOMException && falha.name === 'AbortError')) {
      throw new ApiError({ message: 'Solicitação cancelada.', status: 0, kind: 'cancelled' });
    }
    throw new ApiError({
      message: 'Não foi possível conectar ao servidor.',
      status: 0,
      kind: 'network',
    });
  } finally {
    sinal.limpar();
  }
}

async function tentarRenovarSessao(): Promise<boolean> {
  refreshEmAndamento ??= (async () => {
    try {
      const resposta = await enviar('POST', '/auth/refresh', undefined, {
        timeoutMs: TIMEOUT_PADRAO_MS,
      });
      if (!resposta.ok) {
        accessToken = null;
        return false;
      }
      const dados = (await resposta.json()) as { accessToken?: unknown };
      if (typeof dados.accessToken !== 'string' || !dados.accessToken) {
        accessToken = null;
        return false;
      }
      accessToken = dados.accessToken;
      return true;
    } catch {
      accessToken = null;
      return false;
    } finally {
      refreshEmAndamento = null;
    }
  })();
  return refreshEmAndamento;
}

interface ProblemDetails {
  readonly codigo?: unknown;
  readonly correlacaoId?: unknown;
  readonly instance?: unknown;
  readonly campos?: unknown;
}

function tipoPorStatus(status: number): ApiErrorKind {
  if (status === 400) return 'bad_request';
  if (status === 401) return 'unauthenticated';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not_found';
  if (status === 409) return 'conflict';
  if (status === 422) return 'validation';
  if (status === 429) return 'rate_limited';
  return 'server';
}

function mensagemPorStatus(status: number): string {
  if (status === 400) return 'Não foi possível entender a solicitação.';
  if (status === 401) return 'Sua sessão expirou. Entre novamente.';
  if (status === 403) return 'Você não tem permissão para esta ação.';
  if (status === 404) return 'O recurso solicitado não foi encontrado.';
  if (status === 409) return 'A alteração conflita com o estado atual.';
  if (status === 422) return 'Verifique os dados informados.';
  if (status === 429) return 'Muitas tentativas. Aguarde um instante.';
  return 'O servidor não conseguiu concluir a solicitação.';
}

function lerCampos(valor: unknown): readonly ApiFieldError[] {
  if (!Array.isArray(valor)) return [];
  return valor.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return [];
    const campo = Reflect.get(item, 'campo');
    const mensagem = Reflect.get(item, 'mensagem');
    return typeof campo === 'string' && typeof mensagem === 'string'
      ? [{ campo, mensagem }]
      : [];
  });
}

async function extrairErro(resposta: Response): Promise<ApiError> {
  let problema: ProblemDetails = {};
  try {
    problema = (await resposta.json()) as ProblemDetails;
  } catch {
    // Proxy e falhas de infraestrutura podem responder HTML; nunca exibimos esse corpo cru.
  }
  const retryAfter = Number.parseInt(resposta.headers.get('Retry-After') ?? '', 10);
  return new ApiError({
    message: mensagemPorStatus(resposta.status),
    status: resposta.status,
    kind: tipoPorStatus(resposta.status),
    codigo: typeof problema.codigo === 'string' ? problema.codigo : null,
    correlacaoId: typeof problema.correlacaoId === 'string'
      ? problema.correlacaoId
      : resposta.headers.get('X-Correlation-ID'),
    instance: typeof problema.instance === 'string' ? problema.instance : null,
    campos: lerCampos(problema.campos),
    retryAfterSeconds: Number.isFinite(retryAfter) ? retryAfter : null,
  });
}

function podeRepetir(metodo: Metodo, opcoes: RequestOptions): boolean {
  if (metodo === 'GET') return true;
  return opcoes.retry === 'idempotent-write' && Boolean(opcoes.idempotencyKey);
}

function respostaTransitoria(status: number): boolean {
  return status === 408 || status === 429 || status >= 500;
}

async function aguardarRepeticao(tentativa: number, retryAfterSeconds: number | null): Promise<void> {
  const indicado = retryAfterSeconds === null ? 0 : retryAfterSeconds * 1_000;
  const exponencial = 100 * (2 ** tentativa) + Math.random() * 75;
  await new Promise((resolve) => setTimeout(resolve, Math.min(2_000, Math.max(indicado, exponencial))));
}

async function requisitar<T>(
  metodo: Metodo,
  caminho: string,
  corpo?: unknown,
  opcoes: RequestOptions = {},
): Promise<T> {
  const maxTentativas = podeRepetir(metodo, opcoes) ? MAX_TENTATIVAS_LEITURA : 1;
  let renovacaoTentada = false;

  for (let tentativa = 0; tentativa < maxTentativas; tentativa += 1) {
    let resposta: Response;
    try {
      resposta = await enviar(metodo, caminho, corpo, opcoes);
    } catch (falha) {
      const erro = falha instanceof ApiError
        ? falha
        : new ApiError({ message: 'Não foi possível conectar ao servidor.', status: 0, kind: 'network' });
      const repetivel = erro.kind === 'network' || erro.kind === 'timeout';
      if (!repetivel || tentativa + 1 >= maxTentativas || opcoes.signal?.aborted) throw erro;
      await aguardarRepeticao(tentativa, null);
      continue;
    }

    if (resposta.status === 401 && !caminho.startsWith('/auth/') && !renovacaoTentada) {
      renovacaoTentada = true;
      if (await tentarRenovarSessao()) {
        tentativa -= 1;
        continue;
      }
    }

    if (!resposta.ok) {
      const erro = await extrairErro(resposta);
      if (respostaTransitoria(resposta.status) && tentativa + 1 < maxTentativas) {
        await aguardarRepeticao(tentativa, erro.retryAfterSeconds);
        continue;
      }
      throw erro;
    }

    if (resposta.status === 204 || resposta.status === 205) return undefined as T;
    const texto = await resposta.text();
    if (!texto) return undefined as T;
    try {
      return JSON.parse(texto) as T;
    } catch {
      throw new ApiError({
        message: 'O servidor respondeu em um formato inesperado.',
        status: resposta.status,
        kind: 'contract',
        correlacaoId: resposta.headers.get('X-Correlation-ID'),
      });
    }
  }

  throw new ApiError({ message: 'Não foi possível conectar ao servidor.', status: 0, kind: 'network' });
}

export const api = {
  get: <T>(caminho: string, opcoes?: RequestOptions) => requisitar<T>('GET', caminho, undefined, opcoes),
  post: <T>(caminho: string, corpo?: unknown, opcoes?: RequestOptions) =>
    requisitar<T>('POST', caminho, corpo, opcoes),
  put: <T>(caminho: string, corpo?: unknown, opcoes?: RequestOptions) =>
    requisitar<T>('PUT', caminho, corpo, opcoes),
  patch: <T>(caminho: string, corpo?: unknown, opcoes?: RequestOptions) =>
    requisitar<T>('PATCH', caminho, corpo, opcoes),
  delete: <T>(caminho: string, opcoes?: RequestOptions) =>
    requisitar<T>('DELETE', caminho, undefined, opcoes),
};

export { tentarRenovarSessao };
