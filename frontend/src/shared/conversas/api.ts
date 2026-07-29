import { api } from '@/lib/api';
import type { ConversaResumo, Mensagem } from './tipos';

/**
 * Acesso REST às conversas.
 *
 * Nenhuma função recebe `tenantId`: ele é resolvido no backend a partir do
 * token verificado. Aceitá-lo aqui daria a impressão de que o cliente escolhe
 * de qual empresa está lendo — e é exatamente essa impressão que o
 * `01-padroes-tecnicos.md` proíbe.
 */
export const conversasApi = {
  listar: () => api.get<ConversaResumo[]>('/conversas'),

  mensagens: (conversaId: string) =>
    api.get<Mensagem[]>(`/conversas/${conversaId}/mensagens`),

  enviar: (conversaId: string, texto: string) =>
    api.post<Mensagem>(`/conversas/${conversaId}/mensagens`, { texto }),
};
