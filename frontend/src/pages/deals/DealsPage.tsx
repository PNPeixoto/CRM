import { Navigate } from 'react-router-dom';

/**
 * Oportunidades e funil são a mesma tela.
 *
 * <p>O esqueleto de rotas previa duas páginas separadas, mas no MVP a lista de
 * oportunidades é o próprio kanban — manter duas telas mostrando os mesmos
 * registros de formas quase iguais divide a atenção e dobra a manutenção. A
 * rota continua existindo para não quebrar link salvo.
 */
export function DealsPage() {
  return <Navigate to="/funis" replace />;
}
