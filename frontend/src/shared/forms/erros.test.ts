import { describe, expect, it } from 'vitest';
import { ApiError } from '@/lib/api';
import {
  focarPrimeiroCampoInvalido,
  mapearFalhaDeFormulario,
  segredoOpcional,
} from './erros';

describe('padrão de erros de formulário', () => {
  it('mapeia somente campos conhecidos do RFC 9457', () => {
    const falha = new ApiError({
      message: 'Verifique os dados informados.',
      status: 400,
      kind: 'bad_request',
      campos: [
        { campo: 'email', mensagem: 'E-mail inválido.' },
        { campo: 'tenantId', mensagem: 'Campo indevido.' },
      ],
    });

    expect(mapearFalhaDeFormulario(falha, ['nome', 'email'] as const)).toEqual({
      campos: { email: 'E-mail inválido.' },
      mensagem: 'Revise os campos destacados e tente novamente.',
    });
  });

  it('leva o foco ao primeiro controle inválido na ordem visual', () => {
    const formulario = document.createElement('form');
    const nome = document.createElement('input');
    nome.name = 'nome';
    const email = document.createElement('input');
    email.name = 'email';
    formulario.append(nome, email);
    document.body.append(formulario);

    focarPrimeiroCampoInvalido(formulario, ['nome', 'email'] as const, {
      nome: 'Informe o nome.',
      email: 'E-mail inválido.',
    });

    expect(document.activeElement).toBe(nome);
    formulario.remove();
  });

  it('omite segredo em branco sem fabricar máscara', () => {
    expect(segredoOpcional('   ')).toBeUndefined();
    expect(segredoOpcional('token-real')).toBe('token-real');
  });
});
