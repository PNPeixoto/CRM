/**
 * Aviso de sessão entre abas.
 *
 * <b>O que trafega aqui é um verbo, nunca um segredo.</b> `BroadcastChannel`
 * é legível por qualquer script da mesma origem, então um token publicado
 * neste canal estaria disponível a um XSS em qualquer aba aberta — inclusive
 * abas que a pessoa esqueceu abertas ontem. Por isso o payload é apenas
 * `{ tipo: 'saiu' }`: quem recebe já sabe o que fazer, e o que ele precisa
 * saber não é secreto.
 *
 * <b>Isto não é fronteira de segurança.</b> O que encerra a sessão de verdade
 * é o servidor invalidar o refresh token. Este canal existe para que a
 * interface das outras abas pare de mentir que ainda há sessão, em vez de
 * descobrir isso no próximo 401.
 */

const NOME_DO_CANAL = 'crm-pnp:sessao:v1';

export type EventoDeSessao = { readonly tipo: 'saiu' };

type Ouvinte = (evento: EventoDeSessao) => void;

function abrirCanal(): BroadcastChannel | null {
  // Navegador antigo, contexto sem suporte ou ambiente de teste sem a API:
  // degrada em silêncio. A alternativa seria falhar o logout por causa de um
  // aviso cosmético.
  if (typeof BroadcastChannel === 'undefined') return null;
  try {
    return new BroadcastChannel(NOME_DO_CANAL);
  } catch {
    return null;
  }
}

export function criarCanalDeSessao(): {
  anunciarSaida: () => void;
  aoReceber: (ouvinte: Ouvinte) => () => void;
  fechar: () => void;
} {
  const canal = abrirCanal();

  return {
    anunciarSaida() {
      try {
        canal?.postMessage({ tipo: 'saiu' } satisfies EventoDeSessao);
      } catch {
        // Aba fechando durante o envio. Nada a fazer, e nada a perder.
      }
    },

    aoReceber(ouvinte) {
      if (!canal) return () => {};
      const adaptador = (mensagem: MessageEvent<unknown>) => {
        const dado = mensagem.data;
        // Valida a forma em vez de confiar: qualquer script da origem pode
        // publicar aqui, e uma mensagem malformada não pode derrubar a aba.
        if (typeof dado === 'object' && dado !== null && Reflect.get(dado, 'tipo') === 'saiu') {
          ouvinte({ tipo: 'saiu' });
        }
      };
      canal.addEventListener('message', adaptador);
      return () => canal.removeEventListener('message', adaptador);
    },

    fechar() {
      try {
        canal?.close();
      } catch {
        // Já fechado.
      }
    },
  };
}
