import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Esconde a aplicação de quem não está autenticado.
 *
 * <b>Isto é conveniência de UX, não fronteira de segurança.</b> Qualquer
 * pessoa pode desabilitar este componente no navegador; o que impede o acesso
 * ao dado é o backend rejeitar a requisição. Se algum dia a proteção de uma
 * rota existir só aqui, o dado já está exposto.
 */
export function RotaProtegida() {
  const { usuario, carregando } = useAuth();
  const localizacao = useLocation();

  if (carregando) {
    // Redirecionar antes de a verificação terminar jogaria para o login todo
    // usuário com sessão válida, a cada F5.
    return (
      <div
        className="flex min-h-dvh items-center justify-center text-sm"
        style={{ color: 'var(--text-muted)' }}
        role="status"
        aria-live="polite"
      >
        Carregando…
      </div>
    );
  }

  if (!usuario) {
    // `state` guarda o destino para devolver o usuário ao lugar certo depois
    // do login, em vez de largá-lo sempre no dashboard.
    return <Navigate to="/login" replace state={{ de: localizacao.pathname }} />;
  }

  return <Outlet />;
}
