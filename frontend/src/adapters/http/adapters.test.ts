import { afterEach, describe, expect, it, vi } from 'vitest';
import { contatosApi } from '@/shared/crm/api';
import { obterApresentacao, obterContextos } from '@/shared/tenant/api';
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
      responsavelLogin: null,
      responsavelNome: null,
      criadoEm: '2026-08-01T12:00:00Z',
    }]);
  });

  it('cria contato com chave idempotente sem colocar a chave no corpo', async () => {
    const http = instalarHttpMock([{
      metodo: 'POST',
      caminho: '/api/contatos',
      json: {
        id: '01900000-0000-7000-8000-000000000001',
        nome: 'Ana',
        tipo: 'PERSON',
        criadoEm: '2026-08-01T12:00:00Z',
      },
    }]);

    await contatosApi.criar({ nome: 'Ana' });

    expect(http.chamadas[0].cabecalho('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/i);
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

  it('converte somente os contextos autorizados publicados pelo backend', async () => {
    instalarHttpMock([{
      caminho: '/api/organizacao/contextos',
      json: {
        tenantId: '01900000-0000-7000-8000-000000000001',
        membershipId: '01900000-0000-7000-8000-000000000002',
        contexts: [{
          id: '01900000-0000-7000-8000-000000000003',
          type: 'UNIT',
          code: 'centro',
          name: 'Unidade Centro',
          roles: ['ATENDENTE'],
          permissions: ['contacts.read'],
          scopes: ['UNIT'],
        }],
      },
    }]);

    await expect(obterContextos()).resolves.toMatchObject({
      tenantId: '01900000-0000-7000-8000-000000000001',
      contextos: [{ nome: 'Unidade Centro', tipo: 'UNIT', escopos: ['UNIT'] }],
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
