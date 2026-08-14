import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { renderComEstadoServidor } from '@/test/estadoServidor';
import { instalarHttpMock } from '@/test/http';
import { CalendarPage } from './CalendarPage';

describe('Agenda', () => {
  beforeEach(() => {
    instalarHttpMock([{
      caminho: '/api/tarefas?apenasAbertas=false',
      json: [
        {
          id: 'task-1',
          titulo: 'Apresentação comercial',
          descricao: 'Revisar os números finais.',
          vencimentoEm: '2026-08-17T13:30:00Z',
          concluidaEm: null,
          responsavelId: 'user-1',
          responsavelLogin: 'alex',
          responsavelNome: 'Alex Peixoto',
          contatoId: null,
          oportunidadeId: null,
          criadaEm: '2026-08-10T12:00:00Z',
        },
      ],
    }]);
  });

  it('permite localizar um compromisso pelo mês e abrir o detalhe do dia', async () => {
    renderComEstadoServidor(<CalendarPage />);

    const ocorrencias = await screen.findAllByText(/Apresentação comercial/);
    expect(ocorrencias.length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', {
      name: /17 de ago.*Apresentação comercial/i,
    }));

    expect(screen.getByRole('checkbox', {
      name: 'Concluir Apresentação comercial',
    })).toBeInTheDocument();
    expect(screen.getByText('10:30 · Alex Peixoto (@alex)')).toBeInTheDocument();
  });
});
