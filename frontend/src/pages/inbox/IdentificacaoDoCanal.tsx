import { Camera, Globe2, MessageCircle, Send } from 'lucide-react';
import type { ComponentType } from 'react';
import type { TipoCanal } from '@/shared/crm/tipos';

interface AparenciaDoCanal {
  readonly rotulo: string;
  readonly detalhe: string;
  readonly Icone: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
  readonly cor: string;
  readonly fundo: string;
}

const CANAIS: Record<TipoCanal, AparenciaDoCanal> = {
  LIVE_CHAT: {
    rotulo: 'Chat do site', detalhe: 'Live chat', Icone: Globe2,
    cor: 'var(--info)', fundo: 'var(--info-soft)',
  },
  TELEGRAM: {
    rotulo: 'Telegram', detalhe: 'Telegram Bot API', Icone: Send,
    cor: 'var(--info)', fundo: 'var(--info-soft)',
  },
  WHATSAPP_CLOUD: {
    rotulo: 'WhatsApp', detalhe: 'WhatsApp Cloud API', Icone: MessageCircle,
    cor: 'var(--success)', fundo: 'var(--success-soft)',
  },
  WHATSAPP_EVOLUTION: {
    rotulo: 'WhatsApp', detalhe: 'Evolution', Icone: MessageCircle,
    cor: 'var(--success)', fundo: 'var(--success-soft)',
  },
  INSTAGRAM: {
    rotulo: 'Instagram', detalhe: 'Instagram Messaging', Icone: Camera,
    cor: 'var(--brand)', fundo: 'var(--brand-soft)',
  },
};

export function IdentificacaoDoCanal({ tipo }: { readonly tipo: TipoCanal | null }) {
  if (!tipo) {
    return (
      <span className="inline-flex items-center rounded px-2 py-1 text-xs text-[var(--text-muted)] bg-[var(--surface-sunken)]">
        Canal indisponível
      </span>
    );
  }
  const canal = CANAIS[tipo];
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded px-2 py-1 text-xs font-medium"
      style={{ color: canal.cor, backgroundColor: canal.fundo }}
      title={canal.detalhe}
    >
      <canal.Icone className="size-3.5" aria-hidden />
      {canal.rotulo}
    </span>
  );
}
