import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { canaisApi } from '@/shared/crm/api';
import { IntegrationsPage } from './IntegrationsPage';
import { renderComEstadoServidor } from '@/test/estadoServidor';

vi.mock('@/shared/crm/api', () => ({
  canaisApi: {
    listar: vi.fn(),
    criar: vi.fn(),
    alternarAtivacao: vi.fn(),
  },
}));

describe('IntegrationsPage', () => {
  beforeEach(() => {
    vi.mocked(canaisApi.listar).mockRejectedValue(new Error('forbidden'));
  });

  it('shows a load failure without leaving an unhandled rejection', async () => {
    renderComEstadoServidor(<IntegrationsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/carregar os canais/i);
    expect(screen.queryByText(/Nenhum canal conectado/i)).not.toBeInTheDocument();
  });

  it('mantém segredos vazios e não os envia sem intenção explícita', async () => {
    vi.mocked(canaisApi.listar).mockResolvedValue([]);
    vi.mocked(canaisApi.criar).mockResolvedValue({
      id: 'canal-1', tipo: 'TELEGRAM', nome: 'Atendimento',
      identificadorExterno: null, ativo: true, temToken: false, temSegredoWebhook: false,
      estadoRemoto: null, pendenciasRemotas: null,
      ultimaReconciliacaoEm: null, ultimaFalhaRemotaEm: null,
    });
    renderComEstadoServidor(<IntegrationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /conectar canal/i }));
    expect(screen.getByLabelText('Token do bot')).toHaveValue('');
    expect(screen.queryByLabelText('Segredo do webhook')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Atendimento' } });
    fireEvent.change(screen.getByLabelText('Token do bot'), { target: { value: '123:teste' } });
    fireEvent.click(screen.getByRole('button', { name: /^conectar$/i }));

    await waitFor(() => expect(canaisApi.criar).toHaveBeenCalledOnce());
    expect(vi.mocked(canaisApi.criar).mock.calls[0][0]).toEqual({
      tipo: 'TELEGRAM',
      nome: 'Atendimento',
      identificadorExterno: undefined,
      token: '123:teste',
      segredoWebhook: undefined,
      tokenVerificacaoWebhook: undefined,
    });
  });

  it('conecta a instancia Evolution sem expor a chave global no navegador', async () => {
    vi.mocked(canaisApi.listar).mockResolvedValue([]);
    vi.mocked(canaisApi.criar).mockResolvedValue({
      id: 'canal-evolution', tipo: 'WHATSAPP_EVOLUTION', nome: 'WhatsApp de teste',
      identificadorExterno: 'pnp-teste', ativo: true, temToken: true, temSegredoWebhook: true,
      estadoRemoto: null, pendenciasRemotas: null,
      ultimaReconciliacaoEm: null, ultimaFalhaRemotaEm: null,
    });
    renderComEstadoServidor(<IntegrationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /conectar canal/i }));
    fireEvent.change(screen.getByLabelText('Tipo'), {
      target: { value: 'WHATSAPP_EVOLUTION' },
    });
    fireEvent.change(screen.getByLabelText('Nome'), {
      target: { value: 'WhatsApp de teste' },
    });
    fireEvent.change(screen.getByLabelText(/nome da inst/i), {
      target: { value: 'pnp-teste' },
    });
    expect(screen.queryByLabelText(/token/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /^conectar$/i }));

    await waitFor(() => expect(canaisApi.criar).toHaveBeenCalledOnce());
    expect(vi.mocked(canaisApi.criar).mock.calls[0][0]).toEqual({
      tipo: 'WHATSAPP_EVOLUTION',
      nome: 'WhatsApp de teste',
      identificadorExterno: 'pnp-teste',
      token: undefined,
      segredoWebhook: undefined,
      tokenVerificacaoWebhook: undefined,
    });
  });

  it('conecta o Instagram oficial sem reexibir os segredos', async () => {
    const canalInstagram = {
      id: 'canal-instagram', tipo: 'INSTAGRAM', nome: 'Instagram PNP',
      identificadorExterno: '17841400000000000', ativo: true,
      temToken: true, temSegredoWebhook: true,
      estadoRemoto: 'CHECKING', pendenciasRemotas: 1,
      ultimaReconciliacaoEm: null, ultimaFalhaRemotaEm: null,
    } as const;
    vi.mocked(canaisApi.listar)
      .mockResolvedValueOnce([])
      .mockResolvedValue([canalInstagram]);
    vi.mocked(canaisApi.criar).mockResolvedValue(canalInstagram);
    renderComEstadoServidor(<IntegrationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /conectar canal/i }));
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'INSTAGRAM' } });
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Instagram PNP' } });
    fireEvent.change(screen.getByLabelText(/id da conta profissional/i), {
      target: { value: '17841400000000000' },
    });
    fireEvent.change(screen.getByLabelText(/token de acesso/i), {
      target: { value: 'IGAA-acesso' },
    });
    fireEvent.change(screen.getByLabelText(/segredo do aplicativo/i), {
      target: { value: 'app-secret' },
    });
    fireEvent.change(screen.getByLabelText(/token de verificação/i), {
      target: { value: 'verify-secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^conectar$/i }));

    await waitFor(() => expect(canaisApi.criar).toHaveBeenCalledOnce());
    expect(vi.mocked(canaisApi.criar).mock.calls[0][0]).toEqual({
      tipo: 'INSTAGRAM',
      nome: 'Instagram PNP',
      identificadorExterno: '17841400000000000',
      token: 'IGAA-acesso',
      segredoWebhook: 'app-secret',
      tokenVerificacaoWebhook: 'verify-secret',
    });
    expect(await screen.findByText(/callback na meta/i)).toHaveTextContent(
      '/api/webhooks/meta/canal-instagram',
    );
    expect(screen.queryByText('IGAA-acesso')).not.toBeInTheDocument();
    expect(screen.queryByText('app-secret')).not.toBeInTheDocument();
  });
});
