import { EstadoDeConteudo } from '@/components/ui/content-state';
import { Pagina } from '@/shared/components/Pagina';

export function PaginaSemPermissao() {
  return (
    <Pagina titulo="Acesso não permitido">
      <EstadoDeConteudo tipo="sem-permissao" />
    </Pagina>
  );
}

export function PaginaNaoEncontrada() {
  return (
    <Pagina titulo="Página não encontrada">
      <EstadoDeConteudo tipo="nao-encontrado" />
    </Pagina>
  );
}
