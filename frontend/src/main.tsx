import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AppRouter } from './app/router';

// Fontes auto-hospedadas, não do Google Fonts. O CSP definido no backend
// declara `font-src 'self' data:` — buscar de fonts.gstatic.com seria
// bloqueado pelo navegador, e a tela cairia para a fonte do sistema sem erro
// visível. Afrouxar o CSP para liberar um domínio de terceiro é o caminho
// errado: a fonte passaria a ser dependência de disponibilidade externa e um
// vetor a mais.
import '@fontsource/manrope/400.css';
import '@fontsource/manrope/500.css';
import '@fontsource/manrope/600.css';
import '@fontsource/manrope/700.css';
import '@fontsource/jetbrains-mono/400.css';

import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppRouter />
  </StrictMode>,
);
