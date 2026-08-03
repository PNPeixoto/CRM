import { afterEach, describe, expect, it, vi } from 'vitest';
import { contatosApi } from '@/shared/crm/api';
import { obterApresentacao } from '@/shared/tenant/api';
import { instalarHttpMock } from '@/test/http';

afterEach(() => vi.restoreAllMocks());

describe('adaptadores do contrato gerado', () => {
  it('converte DTO de contato em modelo de apresentação sem vazar campos de transporte', async () => {
    instalarHttpMock([{
      caminho: '/api/contatos',
      json: [{
        id: '01900000-0000-7000-8000-000000000001',
        nome: 'Ana',
        tipo: 'PERSON',
        criadoEm: '2026-08-01T12:00:00Z',
      }],
    }]);

    await expect(contatosApi.listar()).resolves.toEqual([{
      id: '01900000-0000-7000-8000-000000000001',
      nome: 'Ana',
      email: null,
      telefone: null,
      empresa: null,
      observacoes: null,
      responsavelId: null,
      criadoEm: '2026-08-01T12:00:00Z',
    }]);
  });

  it('mantém separados os schemas de funil comercial e funil de onboarding', async () => {
    instalarHttpMock([{
      caminho: '/api/empresa/apresentacao',
      json: {
        segmento: 'RESTAURANT',
        versaoPreset: 1,
        onboardingConcluido: true,
        navegacao: [{
          routeId: 'inbox',
          rotulo: 'Conversas',
          grupo: 'operacao',
          ordem: 1,
          visivel: true,
        }],
        funilPadrao: {
          nome: 'Atendimento',
          etapas: [{ nome: 'Novo', posicao: 1, ganho: false, perda: false }],
        },
      },
    }]);

    await expect(obterApresentacao()).resolves.toMatchObject({
      segmento: 'RESTAURANT',
      funilPadrao: { nome: 'Atendimento', etapas: [{ nome: 'Novo' }] },
    });
  });

  it('falha de modo seguro quando o backend omite campo obrigatório', async () => {
    instalarHttpMock([{
      caminho: '/api/contatos',
      json: [{ nome: 'Sem identificador', criadoEm: '2026-08-01T12:00:00Z' }],
    }]);

    await expect(contatosApi.listar()).rejects.toMatchObject({
      kind: 'contract',
      message: 'O servidor respondeu em um formato inesperado.',
    });
  });
});
