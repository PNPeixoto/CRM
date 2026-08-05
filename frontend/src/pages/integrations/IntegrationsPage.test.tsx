import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { canaisApi } from '@/shared/crm/api';
import { IntegrationsPage } from './IntegrationsPage';

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
    render(<IntegrationsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/carregar os canais/i);
    expect(screen.queryByText(/Nenhum canal conectado/i)).not.toBeInTheDocument();
  });
});
