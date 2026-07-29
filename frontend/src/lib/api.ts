/**
 * Cliente HTTP central.
 *
 * Adaptado do FinUp, com três mudanças:
 *
 * 1. O access token vive EM MEMÓRIA (módulo), nunca em localStorage. Token em
 *    localStorage é legível por qualquer script injetado — um XSS vira roubo
 *    de sessão persistente. Em memória, o token morre com a aba.
 * 2. No 401, tenta `/api/auth/refresh` UMA ÚNICA VEZ e repete a requisição.
 *    O "uma única vez" não é detalhe: sem ele, um refresh expirado devolve 401
 *    para o próprio refresh, que dispara outro refresh, em laço infinito.
 * 3. Refreshes concorrentes compartilham a mesma promessa. Cinco requisições
 *    falhando juntas ao expirar o token disparariam cinco rotações — e como a
 *    rotação invalida o token anterior, quatro delas seriam interpretadas
 *    pelo backend como REUSO e derrubariam a família inteira. O usuário seria
 *    deslogado justamente por ter várias abas abertas.
 */

const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? '/api';

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

let accessToken: string | null = null;

/** Rotação em andamento, compartilhada por todas as chamadas que esbarrarem no 401. */
let refreshEmAndamento: Promise<boolean> | null = null;

export function definirAccessToken(token: string | null): void {
  accessToken = token;
}

/**
 * Lido pelo cliente STOMP, que precisa do token para o frame CONNECT.
 *
 * <p>Exposto por função e não como variável exportada de propósito: uma
 * variável exportada é capturada por valor no momento do import, e quem
 * importasse antes do login guardaria `null` para sempre.
 */
export function obterAccessToken(): string | null {
  return accessToken;
}

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

function lerCookie(nome: string): string | null {
  const encontrado = document.cookie.match(new RegExp(`(?:^|;\\s*)${nome}=([^;]*)`));
  return encontrado ? decodeURIComponent(encontrado[1]) : null;
}

async function enviar(metodo: Metodo, caminho: string, corpo?: unknown): Promise<Response> {
  const headers: Record<string, string> = {};

  if (corpo !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  if (metodo !== 'GET') {
    // Double-submit: o backend deposita o token no cookie XSRF-TOKEN e espera
    // recebê-lo de volta no cabeçalho. Só código da própria origem consegue
    // ler o cookie, e é isso que a conferência prova.
    const xsrf = lerCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
  }

  return fetch(`${API_URL}${caminho}`, {
    method: metodo,
    headers,
    // Necessário para o cookie de refresh (HttpOnly) e o de CSRF viajarem.
    credentials: 'include',
    body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
  });
}

async function tentarRenovarSessao(): Promise<boolean> {
  refreshEmAndamento ??= (async () => {
    try {
      const resposta = await fetch(`${API_URL}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });
      if (!resposta.ok) {
        accessToken = null;
        return false;
      }
      const dados = (await resposta.json()) as { accessToken: string };
      accessToken = dados.accessToken;
      return true;
    } catch {
      accessToken = null;
      return false;
    } finally {
      // Liberado sempre, inclusive em falha: senão uma rotação malsucedida
      // deixaria a promessa presa e todo 401 seguinte reusaria o resultado
      // negativo para sempre.
      refreshEmAndamento = null;
    }
  })();

  return refreshEmAndamento;
}

async function extrairErro(resposta: Response): Promise<ApiError> {
  let mensagem = `Erro ${resposta.status}`;
  try {
    const dados = (await resposta.json()) as { mensagem?: string };
    if (dados?.mensagem) mensagem = dados.mensagem;
  } catch {
    // Corpo não-JSON (proxy fora do ar, página de erro do servidor):
    // a mensagem genérica já é a correta.
  }
  return new ApiError(mensagem, resposta.status);
}

async function requisitar<T>(metodo: Metodo, caminho: string, corpo?: unknown): Promise<T> {
  let resposta = await enviar(metodo, caminho, corpo);

  if (resposta.status === 401 && !caminho.startsWith('/auth/')) {
    // A exclusão de `/auth/` é o que fecha o laço: um 401 vindo do próprio
    // login ou refresh nunca dispara outro refresh.
    const renovou = await tentarRenovarSessao();
    if (renovou) {
      resposta = await enviar(metodo, caminho, corpo);
    }
  }

  if (!resposta.ok) throw await extrairErro(resposta);

  if (resposta.status === 204) return undefined as T;

  const texto = await resposta.text();
  return (texto ? JSON.parse(texto) : undefined) as T;
}

export const api = {
  get: <T>(caminho: string) => requisitar<T>('GET', caminho),
  post: <T>(caminho: string, corpo?: unknown) => requisitar<T>('POST', caminho, corpo),
  put: <T>(caminho: string, corpo?: unknown) => requisitar<T>('PUT', caminho, corpo),
  patch: <T>(caminho: string, corpo?: unknown) => requisitar<T>('PATCH', caminho, corpo),
  delete: <T>(caminho: string) => requisitar<T>('DELETE', caminho),
};

export { tentarRenovarSessao };
