export type SegmentoDeNegocio =
  | 'GENERAL_SERVICES'
  | 'RESTAURANT'
  | 'CONFECTIONERY'
  | 'RENTAL';

export type GrupoDeNavegacao = 'operacao' | 'gestao' | 'plataforma';

export interface ItemDeApresentacao {
  readonly routeId: string;
  readonly rotulo: string;
  readonly grupo: GrupoDeNavegacao;
  readonly ordem: number;
  readonly visivel: boolean;
}

export interface EtapaDeFunilInicial {
  readonly nome: string;
  readonly posicao: number;
  readonly ganho: boolean;
  readonly perda: boolean;
}

export interface ApresentacaoDoTenant {
  readonly segmento: SegmentoDeNegocio;
  readonly versaoPreset: number;
  readonly onboardingConcluido: boolean;
  readonly navegacao: readonly ItemDeApresentacao[];
  readonly funilPadrao: {
    readonly nome: string;
    readonly etapas: readonly EtapaDeFunilInicial[];
  };
}
