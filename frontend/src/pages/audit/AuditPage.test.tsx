import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { auditoriaApi } from '@/shared/audit/api';
import type { PaginaAuditoria } from '@/shared/audit/tipos';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { renderComEstadoServidor } from '@/test/estadoServidor';
import { AuditPage } from './AuditPage';

afterEach(() => vi.restoreAllMocks());

const PRIMEIRA: PaginaAuditoria = {
  integridadeVerificada: true,
  proximoCursor: 'cursor-seguro-2',
  eventos: [{
    id: '019fe700-0000-7000-8000-000000000001',
    ocorridoEm: '2026-08-09T12:00:00Z',
    acao: 'CREDENTIAL_CHANGED_V1',
    resultado: 'SUCCEEDED',
    motivo: 'CHANNEL_CREDENTIAL_CHANGED',
    atorTipo: 'HUMAN',
    atorId: '019fe700-0000-7000-8000-000000000002',
    atorNome: 'Ana Gestora',
    escopoTipo: 'TENANT',
    escopoId: '019fe700-0000-7000-8000-000000000003',
    alvoTipo: 'CHANNEL_CONNECTION',
    alvoId: '019fe700-0000-7000-8000-000000000004',
    correlacaoId: '019fe700-0000-7000-8000-000000000005',
  }],
};

describe('auditoria', () => {
  it('exibe somente metadados mascarados, integridade e pagina no servidor', async () => {
    const listar = vi.spyOn(auditoriaApi, 'listar')
      .mockResolvedValueOnce(PRIMEIRA)
      .mockResolvedValueOnce({ ...PRIMEIRA, proximoCursor: null, eventos: [] });
    const { container } = renderComEstadoServidor(<AuditPage />);

    expect(await screen.findByText('Ana Gestora')).toBeInTheDocument();
    expect(screen.getAllByText('Credencial alterada').length).toBeGreaterThan(0);
    expect(screen.getByText('Integridade verificada')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('019fe700-0000-7000-8000-000000000004');
    expect(screen.getAllByText('019fe700â€¦').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'PrÃ³xima pÃ¡gina' }));
    await waitFor(() => expect(listar).toHaveBeenLastCalledWith(
      { acao: '', resultado: '' }, 'cursor-seguro-2', expect.any(AbortSignal),
    ));
    expect(await screen.findByText('PÃ¡gina 2')).toBeInTheDocument();
    await esperarSemViolacoesAcessiveis(container);
  });

  it('reinicia o cursor e envia filtros canonicos ao backend', async () => {
    const listar = vi.spyOn(auditoriaApi, 'listar').mockResolvedValue({
      eventos: [], proximoCursor: null, integridadeVerificada: true,
    });
    renderComEstadoServidor(<AuditPage />);
    await waitFor(() => expect(listar).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('AÃ§Ã£o'), {
      target: { value: 'AUTHORIZATION_DENIED' },
    });
    fireEvent.change(screen.getByLabelText('Resultado'), {
      target: { value: 'DENIED' },
    });

    await waitFor(() => expect(listar).toHaveBeenLastCalledWith(
      { acao: 'AUTHORIZATION_DENIED', resultado: 'DENIED' },
      null,
      expect.any(AbortSignal),
    ));
  });
});
