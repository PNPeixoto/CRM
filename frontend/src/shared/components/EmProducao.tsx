interface EmProducaoProps {
  readonly titulo: string;
  readonly descricao?: string;
}

/**
 * Placeholder padrão das páginas ainda não implementadas.
 * A rota funciona e o arquivo existe — só o conteúdo está pendente.
 */
export function EmProducao({ titulo, descricao }: EmProducaoProps) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <span className="rounded-full border px-3 py-1 text-xs font-medium uppercase tracking-wide">
        Em produção
      </span>
      <h1 className="text-xl font-semibold">{titulo}</h1>
      <p className="max-w-md text-sm text-muted">
        {descricao ?? 'Este módulo ainda está em desenvolvimento e será liberado em breve.'}
      </p>
    </div>
  );
}
