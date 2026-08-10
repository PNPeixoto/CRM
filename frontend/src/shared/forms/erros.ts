import { ApiError } from '@/lib/api';

export interface FalhaDeFormulario<Campo extends string> {
  readonly campos: Partial<Record<Campo, string>>;
  readonly mensagem: string;
}

/** Converte apenas campos que o formulário realmente possui; nomes inesperados não viram seletores. */
export function mapearFalhaDeFormulario<Campo extends string>(
  falha: unknown,
  camposPermitidos: readonly Campo[],
): FalhaDeFormulario<Campo> {
  const permitidos = new Set<string>(camposPermitidos);
  const campos: Partial<Record<Campo, string>> = {};

  if (falha instanceof ApiError) {
    for (const erro of falha.campos) {
      if (permitidos.has(erro.campo) && campos[erro.campo as Campo] === undefined) {
        campos[erro.campo as Campo] = erro.mensagem;
      }
    }
    return {
      campos,
      mensagem: Object.keys(campos).length > 0
        ? 'Revise os campos destacados e tente novamente.'
        : falha.message,
    };
  }

  return {
    campos,
    mensagem: 'Não foi possível salvar. Tente novamente.',
  };
}

export function focarPrimeiroCampoInvalido<Campo extends string>(
  formulario: HTMLFormElement,
  ordem: readonly Campo[],
  erros: Partial<Record<Campo, string>>,
): void {
  const primeiro = ordem.find((campo) => erros[campo]);
  if (!primeiro) return;
  const controle = formulario.elements.namedItem(primeiro);
  if (controle instanceof HTMLElement) controle.focus();
}

/** Em edição, vazio significa “não alterar”; nunca fabricamos um valor mascarado. */
export function segredoOpcional(valor: string): string | undefined {
  const normalizado = valor.trim();
  return normalizado || undefined;
}
