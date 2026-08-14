import type { TipoCanal } from '@/shared/crm/tipos';

const NOMES_DOS_CANAIS: Record<TipoCanal, string> = {
  LIVE_CHAT: 'Chat do site',
  TELEGRAM: 'Telegram',
  WHATSAPP_CLOUD: 'WhatsApp',
  WHATSAPP_EVOLUTION: 'WhatsApp',
  INSTAGRAM: 'Instagram',
};

export function rotuloDoCanal(tipo: TipoCanal | null): string {
  return tipo ? NOMES_DOS_CANAIS[tipo] : 'canal não identificado';
}

export function formatarIdentificadorDoContato(valor: string): string {
  const semDominio = valor.replace(/@(s\.whatsapp\.net|c\.us|g\.us)$/i, '');
  if (!/^\d{10,15}$/.test(semDominio)) return valor;
  if (semDominio.startsWith('55') && (semDominio.length === 12 || semDominio.length === 13)) {
    const ddd = semDominio.slice(2, 4);
    const numero = semDominio.slice(4);
    const corte = numero.length === 9 ? 5 : 4;
    return `+55 (${ddd}) ${numero.slice(0, corte)}-${numero.slice(corte)}`;
  }
  return `+${semDominio}`;
}
