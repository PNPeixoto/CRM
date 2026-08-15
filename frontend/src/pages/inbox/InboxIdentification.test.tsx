import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ConversaResumo, Mensagem } from '@/shared/conversas/tipos';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { Conversa } from './Conversa';
import { ListaDeConversas } from './ListaDeConversas';
import { formatarIdentificadorDoContato } from './identificacao';

const CONVERSA: ConversaResumo = {
  id: 'conversation-1',
  channelConnectionId: 'channel-1',
  canalTipo: 'WHATSAPP_EVOLUTION',
  canalNome: 'WhatsApp Vendas',
  canalIdentificador: '551130001234',
  contatoNome: 'Maria Silva',
  contatoIdentificador: '5511998765432@s.whatsapp.net',
  status: 'OPEN',
  atendenteId: 'user-1',
  atendenteNome: 'Alex Operador',
  ultimaMensagemEm: '2026-08-14T15:30:00Z',
  venceEm: null,
  versao: 3,
};

const CONVERSA_INSTAGRAM: ConversaResumo = {
  ...CONVERSA,
  id: 'conversation-instagram',
  channelConnectionId: 'channel-instagram',
  canalTipo: 'INSTAGRAM',
  canalNome: 'Instagram Comercial',
  canalIdentificador: '@finup.oficial',
  contatoNome: 'Marina Costa',
  contatoIdentificador: '@marina.costa',
};

const MENSAGENS: readonly Mensagem[] = [
  {
    id: 'message-1',
    direcao: 'INBOUND',
    tipoConteudo: 'TEXT',
    texto: 'Olá, preciso de ajuda.',
    status: 'RECEIVED',
    autorId: null,
    autorNome: null,
    criadaEm: '2026-08-14T15:29:00Z',
    versao: 1,
  },
  {
    id: 'message-2',
    direcao: 'OUTBOUND',
    tipoConteudo: 'TEXT',
    texto: 'Vou cuidar disso agora.',
    status: 'READ',
    autorId: 'user-1',
    autorNome: 'Alex Operador',
    criadaEm: '2026-08-14T15:30:00Z',
    versao: 1,
  },
];

describe('identificação operacional da Inbox', () => {
  it('mostra contato, número, canal e conexão na lista', async () => {
    const { container } = render(
      <ListaDeConversas
        conversas={[CONVERSA]}
        selecionada={CONVERSA.id}
        aoSelecionar={vi.fn()}
        carregando={false}
        temMais={false}
        carregandoMais={false}
        aoCarregarMais={vi.fn()}
      />,
    );

    expect(screen.getByText('Maria Silva')).toBeInTheDocument();
    expect(screen.getByText('+55 (11) 99876-5432')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Lista de conversas' }))
      .getByText('WhatsApp')).toBeInTheDocument();
    expect(screen.getByText('WhatsApp Vendas')).toBeInTheDocument();
    expect(screen.getByText('Aberta')).toBeInTheDocument();
    expect(screen.getByText('Atendente Alex Operador')).toBeInTheDocument();
    await esperarSemViolacoesAcessiveis(container);
  });

  it('filtra, identifica e abre a conversa do Instagram', async () => {
    const aoSelecionar = vi.fn();
    render(
      <ListaDeConversas
        conversas={[CONVERSA, CONVERSA_INSTAGRAM]}
        selecionada={CONVERSA.id}
        aoSelecionar={aoSelecionar}
        carregando={false}
        temMais={false}
        carregandoMais={false}
        aoCarregarMais={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText('Filtrar por canal'), {
      target: { value: 'INSTAGRAM' },
    });

    expect(screen.getByText('Marina Costa')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Lista de conversas' }))
      .getByText('Instagram')).toBeInTheDocument();
    expect(screen.getByText('Instagram Comercial')).toBeInTheDocument();
    expect(screen.queryByText('Maria Silva')).not.toBeInTheDocument();
    await waitFor(() => expect(aoSelecionar).toHaveBeenCalledWith(CONVERSA_INSTAGRAM.id));
  });

  it('identifica quem escreveu e quem está respondendo', () => {
    render(
      <Conversa
        mensagens={MENSAGENS}
        carregando={false}
        temMais={false}
        carregandoAnteriores={false}
        aoCarregarAnteriores={vi.fn()}
        aoEnviar={vi.fn()}
        contatoNome="Maria Silva"
        canalTipo="WHATSAPP_EVOLUTION"
        respondendoComo="Alex Operador"
      />,
    );

    expect(screen.getByText('Maria Silva')).toBeInTheDocument();
    expect(screen.getAllByText('Alex Operador')).toHaveLength(2);
    expect(screen.getByLabelText('Respondendo como Alex Operador via WhatsApp'))
      .toBeInTheDocument();
    expect(screen.getByText('Lida')).toBeInTheDocument();
  });

  it('preserva identificadores não telefônicos', () => {
    expect(formatarIdentificadorDoContato('@cliente_telegram')).toBe('@cliente_telegram');
  });
});
