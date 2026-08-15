import http from 'node:http';
import { createHash, randomUUID } from 'node:crypto';

const tenantId = '018f0000-0000-7000-8000-000000000001';
const userId = '018f0000-0000-7000-8000-000000000002';
const membershipId = '018f0000-0000-7000-8000-000000000003';

let tarefas = [
  tarefa('task-1', 'Apresentação comercial', '2026-08-17T13:30:00Z', null, 'Alex Peixoto'),
  tarefa('task-2', 'Follow-up com Maria Silva', '2026-08-17T16:00:00Z', null, 'Carla Mendes', 'contact-1', 'deal-1'),
  tarefa('task-3', 'Enviar proposta Grupo Horizonte', '2026-08-18T14:00:00Z', null, 'Alex Peixoto', 'contact-1', 'deal-1'),
  tarefa('task-4', 'Reunião de pipeline', '2026-08-20T12:00:00Z', null, 'Equipe Comercial'),
  tarefa('task-5', 'Revisar contrato Loja Aurora', '2026-08-13T18:00:00Z', null, 'Bruno Costa', 'contact-3', 'deal-2'),
  tarefa('task-6', 'Atualizar cadastro do lead', null, null, 'Alex Peixoto', 'contact-2', 'deal-3'),
  tarefa('task-7', 'Diagnóstico inicial concluído', '2026-08-12T15:00:00Z', '2026-08-12T16:00:00Z', 'Carla Mendes', 'contact-1', 'deal-1'),
];

let contatos = [
  contato('contact-1', 'Maria Silva', 'maria@horizonte.com.br', '+55 (11) 99876-5432', 'Grupo Horizonte', 'Carla Mendes', '2026-07-18T13:00:00Z'),
  contato('contact-2', 'João Ribeiro', 'joao@ribeirovarejo.com.br', '+55 (21) 99122-8844', 'Ribeiro Varejo', 'Alex Peixoto', '2026-07-22T15:30:00Z'),
  contato('contact-3', 'Fernanda Lima', 'fernanda@lojaaurora.com.br', '+55 (11) 3210-9876', 'Loja Aurora', 'Bruno Costa', '2026-07-25T11:20:00Z'),
  contato('contact-4', 'Camila Torres', 'camila@clinicairis.com.br', '+55 (31) 98870-4421', 'Clínica Íris', 'Carla Mendes', '2026-08-02T17:10:00Z'),
  contato('contact-5', 'Eduardo Ramos', 'eduardo@novabase.com.br', '+55 (41) 99730-1188', 'Nova Base Serviços', 'Alex Peixoto', '2026-08-05T14:45:00Z'),
  contato('contact-6', 'Paulo Menezes', 'paulo@orbeengenharia.com.br', '+55 (51) 99204-3010', 'Orbe Engenharia', 'Bruno Costa', '2026-08-09T12:05:00Z'),
  contato('contact-7', 'Renata Alves', 'renata@movecowork.com.br', '+55 (11) 99555-0182', 'Move Cowork', 'Carla Mendes', '2026-08-11T16:40:00Z'),
  contato('contact-8', 'Lucas Nogueira', 'lucas@verticeauto.com.br', '+55 (19) 99108-7720', 'Vértice Auto', 'Alex Peixoto', '2026-08-13T18:15:00Z'),
];

const funilComercial = {
  id: 'pipeline-commercial', nome: 'Comercial', padrao: true,
  etapas: [
    etapa('stage-new', 'Novo lead', 1),
    etapa('stage-qualified', 'Qualificação', 2),
    etapa('stage-proposal', 'Proposta', 3),
    etapa('stage-negotiation', 'Negociação', 4),
    etapa('stage-won', 'Ganho', 5, true),
    etapa('stage-lost', 'Perdido', 6, false, true),
  ],
};

let oportunidades = [
  oportunidade('deal-1', 'Expansão Grupo Horizonte', 'stage-negotiation', 'contact-1', 12800000, 'Carla Mendes'),
  oportunidade('deal-2', 'Implantação Loja Aurora', 'stage-proposal', 'contact-3', 5400000, 'Bruno Costa'),
  oportunidade('deal-3', 'Operação Ribeiro Varejo', 'stage-qualified', 'contact-2', 3600000, 'Alex Peixoto'),
  oportunidade('deal-4', 'Atendimento multicanal Clínica Íris', 'stage-proposal', 'contact-4', 4800000, 'Carla Mendes'),
  oportunidade('deal-5', 'Automação Nova Base', 'stage-new', 'contact-5', 2200000, 'Alex Peixoto'),
  oportunidade('deal-6', 'Central comercial Orbe', 'stage-negotiation', 'contact-6', 7600000, 'Bruno Costa'),
  oportunidade('deal-7', 'Plano Move Cowork', 'stage-won', 'contact-7', 3100000, 'Carla Mendes', 'WON'),
  oportunidade('deal-8', 'Projeto Vértice Auto', 'stage-lost', 'contact-8', 2800000, 'Alex Peixoto', 'LOST'),
];

let papeis = [papel('owner', 'OWNER', 'Proprietário', true, ['organization.manage'])];
const definicoesDoPreset = [
  ['SDR', 'SDR / Pré-vendas', 'Qualifica leads e organiza follow-ups.'],
  ['CLOSER', 'Closer', 'Conduz propostas e fechamentos.'],
  ['ATENDENTE', 'Atendente', 'Responde conversas e registra próximos passos.'],
  ['GESTOR_ATENDIMENTO', 'Gestor de atendimento', 'Acompanha canais e a fila da equipe.'],
  ['GERENTE_COMERCIAL', 'Gerente comercial', 'Gerencia carteira, funil e indicadores.'],
];

const conversas = [
  conversa('5', 'Marina Costa', '@marina.costa', 'INSTAGRAM', 'Instagram Comercial', '@finup.oficial', 'OPEN', 'Carla Mendes', '2026-08-14T19:42:00Z'),
  conversa('1', 'Maria Silva', '5511998765432@s.whatsapp.net', 'WHATSAPP_EVOLUTION', 'WhatsApp Vendas', '551130001234', 'OPEN', 'Carla Mendes', '2026-08-14T19:34:00Z'),
  conversa('2', 'João Ribeiro', '@joao_ribeiro', 'TELEGRAM', 'Bot Pré-vendas', '@crm_comercial_bot', 'PENDING', 'Alex Peixoto', '2026-08-14T19:20:00Z'),
  conversa('3', 'Loja Aurora', '551132109876', 'WHATSAPP_CLOUD', 'WhatsApp Suporte', '551140005555', 'OPEN', 'Bruno Costa', '2026-08-14T18:52:00Z'),
  conversa('4', 'Visitante #1842', 'visitor_1842', 'LIVE_CHAT', 'Chat do site', 'crm.exemplo.com.br', 'CLOSED', null, '2026-08-14T16:10:00Z'),
];

const mensagens = {
  '5': [
    mensagem('51', 'INBOUND', 'Oi! Vi o post sobre atendimento multicanal e queria entender os planos.', 'RECEIVED', null, '2026-08-14T19:36:00Z'),
    mensagem('52', 'OUTBOUND', 'Oi, Marina! Sou a Carla, da FinUp. Posso te explicar por aqui mesmo.', 'READ', 'Carla Mendes', '2026-08-14T19:39:00Z'),
    mensagem('53', 'INBOUND', 'Perfeito. Somos uma equipe de 12 pessoas e usamos Instagram e WhatsApp.', 'RECEIVED', null, '2026-08-14T19:42:00Z'),
  ],
  '1': [
    mensagem('11', 'INBOUND', 'Olá, vi a proposta e queria tirar uma dúvida.', 'RECEIVED', null, '2026-08-14T19:28:00Z'),
    mensagem('12', 'OUTBOUND', 'Olá, Maria! Claro. É sobre prazo ou condições de pagamento?', 'READ', 'Carla Mendes', '2026-08-14T19:30:00Z'),
    mensagem('13', 'INBOUND', 'Sobre o prazo. Conseguimos começar ainda este mês?', 'RECEIVED', null, '2026-08-14T19:32:00Z'),
    mensagem('14', 'OUTBOUND', 'Sim. Reservamos a última semana para o onboarding e confirmo o cronograma hoje.', 'DELIVERED', 'Carla Mendes', '2026-08-14T19:34:00Z'),
  ],
  '2': [
    mensagem('21', 'INBOUND', 'Vocês atendem empresas com várias unidades?', 'RECEIVED', null, '2026-08-14T19:18:00Z'),
    mensagem('22', 'OUTBOUND', 'Atendemos sim. Quantas unidades vocês têm hoje?', 'SENT', 'Alex Peixoto', '2026-08-14T19:20:00Z'),
  ],
  '3': [mensagem('31', 'INBOUND', 'Preciso atualizar os dados do contrato.', 'RECEIVED', null, '2026-08-14T18:52:00Z')],
  '4': [mensagem('41', 'INBOUND', 'Obrigado pelo atendimento!', 'RECEIVED', null, '2026-08-14T16:10:00Z')],
};

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://localhost:8080');
  const path = url.pathname;
  if (request.method === 'OPTIONS') return send(response, 204);

  if (request.method === 'POST' && path === '/api/auth/refresh') {
    return json(response, { accessToken: 'demo-local-token' });
  }
  if (request.method === 'GET' && path === '/api/auth/me') {
    return json(response, { id: userId, tenantId, login: 'alex', nomeCompleto: 'Alex Peixoto' });
  }
  if (request.method === 'POST' && path === '/api/auth/logout') return send(response, 204);

  if (request.method === 'GET' && path === '/api/empresa/apresentacao') {
    return json(response, {
      segmento: 'GENERAL_SERVICES', versaoPreset: 1, onboardingConcluido: true,
      navegacao: [
        nav('dashboard', 'Visão geral', 'operacao', 1),
        nav('contacts', 'Contatos', 'operacao', 2),
        nav('pipelines', 'Oportunidades', 'operacao', 3),
        nav('inbox', 'Conversas', 'operacao', 4),
        nav('calendar', 'Agenda', 'operacao', 5),
        nav('tasks', 'Tarefas', 'operacao', 6),
        nav('teams', 'Acessos', 'plataforma', 7),
      ],
      funilPadrao: { nome: 'Comercial', etapas: [
        { nome: 'Novo lead', posicao: 1, ganho: false, perda: false },
        { nome: 'Proposta', posicao: 2, ganho: false, perda: false },
        { nome: 'Fechado', posicao: 3, ganho: true, perda: false },
      ] },
    });
  }
  if (request.method === 'GET' && path === '/api/organizacao/permissoes') {
    return json(response, {
      'conversations.read': 'TENANT', 'conversations.write': 'TENANT',
      'contacts.read': 'TENANT', 'contacts.write': 'TENANT',
      'deals.read': 'TENANT', 'deals.write': 'TENANT',
      'reports.read': 'TENANT',
      'tasks.read': 'TENANT', 'tasks.write': 'TENANT',
      'organization.manage': 'TENANT',
    });
  }
  if (request.method === 'GET' && path === '/api/organizacao/contextos') {
    return json(response, {
      tenantId, membershipId,
      contexts: [{ id: tenantId, type: 'TENANT', code: 'FINUP', name: 'FinUp Comercial',
        roles: ['OWNER'], permissions: ['reports.read', 'contacts.read', 'deals.read', 'tasks.read', 'conversations.read'], scopes: ['TENANT'] }],
    });
  }

  if (request.method === 'GET' && path === '/api/relatorios/visao-geral') {
    const abertas = oportunidades.filter((item) => item.status === 'OPEN');
    return json(response, {
      conversasAbertas: conversas.filter((item) => item.status === 'OPEN').length,
      conversasAguardando: conversas.filter((item) => item.status === 'PENDING').length,
      mensagensRecebidasHoje: 20,
      totalDeContatos: contatos.length,
      oportunidadesAbertas: abertas.length,
      valorAbertoCentavos: abertas.reduce((total, item) => total + item.valorCentavos, 0),
      oportunidadesGanhas: 4, valorGanhoCentavos: 18400000, oportunidadesPerdidas: 2,
      tarefasAbertas: tarefas.filter((item) => !item.concluidaEm).length,
      tarefasAtrasadas: 1, canaisAtivos: 4, taxaDeConversaoPercentual: 66.7,
    });
  }

  if (request.method === 'GET' && path === '/api/contatos') {
    const busca = (url.searchParams.get('busca') ?? '').trim().toLocaleLowerCase('pt-BR');
    const pagina = Math.max(0, Number.parseInt(url.searchParams.get('pagina') ?? '0', 10) || 0);
    const tamanho = Math.max(1, Number.parseInt(url.searchParams.get('tamanho') ?? '20', 10) || 20);
    const filtrados = contatos.filter((item) => !busca
      || [item.nome, item.email, item.telefone, item.empresa]
        .some((valor) => valor?.toLocaleLowerCase('pt-BR').includes(busca)));
    return json(response, filtrados.slice(pagina * tamanho, (pagina + 1) * tamanho));
  }
  if (request.method === 'POST' && path === '/api/contatos') {
    const body = await readJson(request);
    const novo = contato(randomUUID(), body.nome, body.email ?? null, body.telefone ?? null,
      body.empresa ?? null, 'Alex Peixoto', new Date().toISOString());
    contatos = [...contatos, novo];
    return json(response, novo);
  }
  const detalheContato = path.match(/^\/api\/contatos\/([^/]+)$/);
  if (request.method === 'GET' && detalheContato) {
    const encontrado = contatos.find((item) => item.id === detalheContato[1]);
    return encontrado
      ? json(response, encontrado)
      : json(response, { codigo: 'NAO_ENCONTRADO' }, 404);
  }
  const oportunidadesDoContato = path.match(/^\/api\/contatos\/([^/]+)\/oportunidades$/);
  if (request.method === 'GET' && oportunidadesDoContato) {
    return json(response, oportunidades.filter((item) => item.contatoId === oportunidadesDoContato[1]));
  }
  if (request.method === 'DELETE' && detalheContato) {
    contatos = contatos.filter((item) => item.id !== detalheContato[1]);
    return send(response, 204);
  }

  if (request.method === 'GET' && path === '/api/funis') return json(response, [funilComercial]);
  if (request.method === 'GET' && path === `/api/funis/${funilComercial.id}/oportunidades`) {
    return json(response, oportunidades);
  }
  if (request.method === 'POST' && path === '/api/oportunidades') {
    const body = await readJson(request);
    const nova = oportunidade(randomUUID(), body.titulo, body.etapaId, body.contatoId ?? null,
      body.valorCentavos ?? 0, 'Alex Peixoto', statusDaEtapa(body.etapaId));
    oportunidades = [...oportunidades, nova];
    return json(response, nova);
  }
  const moverOportunidade = path.match(/^\/api\/oportunidades\/([^/]+)\/mover$/);
  if (request.method === 'POST' && moverOportunidade) {
    const body = await readJson(request);
    const atual = oportunidades.find((item) => item.id === moverOportunidade[1]);
    if (!atual) return json(response, { codigo: 'NAO_ENCONTRADO' }, 404);
    atual.etapaId = body.etapaId;
    atual.status = statusDaEtapa(body.etapaId);
    atual.motivoPerda = body.motivoPerda ?? null;
    return json(response, atual);
  }

  if (request.method === 'GET' && path === '/api/tarefas') {
    const apenasAbertas = url.searchParams.get('apenasAbertas') === 'true';
    const contatoId = url.searchParams.get('contatoId');
    return json(response, tarefas.filter((item) =>
      (!apenasAbertas || !item.concluidaEm) && (!contatoId || item.contatoId === contatoId)));
  }
  if (request.method === 'POST' && path === '/api/tarefas') {
    const body = await readJson(request);
    const nova = tarefa(randomUUID(), body.titulo, body.vencimentoEm ?? null, null, 'Alex Peixoto',
      body.contatoId ?? null, body.oportunidadeId ?? null);
    nova.descricao = body.descricao ?? null;
    tarefas = [...tarefas, nova];
    return json(response, nova);
  }
  const conclusao = path.match(/^\/api\/tarefas\/([^/]+)\/concluir$/);
  if (request.method === 'POST' && conclusao) {
    const atual = tarefas.find((item) => item.id === conclusao[1]);
    if (!atual) return json(response, { codigo: 'NAO_ENCONTRADO' }, 404);
    atual.concluidaEm = atual.concluidaEm ? null : new Date().toISOString();
    return json(response, atual);
  }

  if (request.method === 'GET' && path === '/api/conversas') {
    return json(response, { itens: conversas, temMais: false, sequenciaDoStream: 17 });
  }
  const historico = path.match(/^\/api\/conversas\/([^/]+)\/mensagens$/);
  if (request.method === 'GET' && historico) {
    return json(response, { itens: mensagens[historico[1]] ?? [], temMais: false, sequenciaDoStream: 17 });
  }
  if (request.method === 'POST' && historico) {
    const body = await readJson(request);
    const nova = mensagem(randomUUID(), 'OUTBOUND', body.texto, 'SENT', 'Alex Peixoto', new Date().toISOString());
    mensagens[historico[1]] = [...(mensagens[historico[1]] ?? []), nova];
    return json(response, nova);
  }
  if (request.method === 'GET' && path === '/api/conversas/eventos') {
    return json(response, { eventos: [], temMais: false, resetObrigatorio: false, ultimaSequencia: 17 });
  }

  if (request.method === 'GET' && path === '/api/organizacao/papeis') {
    return json(response, { papeis, permissoes: catalogoDePermissoes() });
  }
  if (request.method === 'POST' && path === '/api/organizacao/papeis/presets/comercial') {
    let criados = 0;
    for (const [codigo, nome, descricao] of definicoesDoPreset) {
      if (papeis.some((item) => item.codigo === codigo)) continue;
      papeis = [...papeis, papel(randomUUID(), codigo, nome, false,
        ['contacts.read', 'deals.read', 'tasks.read', 'conversations.read'], descricao)];
      criados += 1;
    }
    return json(response, { criados, papeis: papeis.filter((item) => item.codigo !== 'OWNER') });
  }
  if (request.method === 'GET' && path === '/api/organizacao/membros') {
    return json(response, [
      { membershipId, usuarioId: userId, login: 'alex', nome: 'Alex Peixoto', atribuicoes: [] },
      { membershipId: 'member-carla', usuarioId: 'user-carla', login: 'carla', nome: 'Carla Mendes', atribuicoes: [] },
      { membershipId: 'member-bruno', usuarioId: 'user-bruno', login: 'bruno', nome: 'Bruno Costa', atribuicoes: [] },
    ]);
  }
  if (request.method === 'GET' && path === '/api/organizacao/equipes') {
    return json(response, [{ gestorId: userId, gestorNome: 'Alex Peixoto', liderados: [
      { usuarioId: 'user-carla', nome: 'Carla Mendes', login: 'carla' },
      { usuarioId: 'user-bruno', nome: 'Bruno Costa', login: 'bruno' },
    ] }]);
  }

  return json(response, { codigo: 'DEMO_NOT_FOUND', path }, 404);
});

server.on('upgrade', (request, socket) => {
  if (request.url !== '/ws') return socket.destroy();
  const key = request.headers['sec-websocket-key'];
  if (!key) return socket.destroy();
  const accept = createHash('sha1')
    .update(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
    .digest('base64');
  const protocolo = request.headers['sec-websocket-protocol']?.split(',')[0]?.trim();
  socket.write([
    'HTTP/1.1 101 Switching Protocols',
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Accept: ${accept}`,
    ...(protocolo ? [`Sec-WebSocket-Protocol: ${protocolo}`] : []),
    '\r\n',
  ].join('\r\n'));

  let buffer = Buffer.alloc(0);
  socket.on('error', () => undefined);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 2) {
      const segundo = buffer[1];
      let tamanho = segundo & 0x7f;
      let deslocamento = 2;
      if (tamanho === 126) {
        if (buffer.length < 4) return;
        tamanho = buffer.readUInt16BE(2);
        deslocamento = 4;
      }
      const mascarada = Boolean(segundo & 0x80);
      const total = deslocamento + (mascarada ? 4 : 0) + tamanho;
      if (buffer.length < total) return;
      const mascara = mascarada ? buffer.subarray(deslocamento, deslocamento + 4) : null;
      deslocamento += mascarada ? 4 : 0;
      const payload = Buffer.from(buffer.subarray(deslocamento, deslocamento + tamanho));
      if (mascara) for (let i = 0; i < payload.length; i += 1) payload[i] ^= mascara[i % 4];
      buffer = buffer.subarray(total);
      const texto = payload.toString('utf8');
      if (texto.startsWith('CONNECT') || texto.startsWith('STOMP')) {
        socket.write(webSocketFrame('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0'));
      }
    }
  });
});

server.listen(8080, '127.0.0.1', () => console.log('CRM demo API em http://127.0.0.1:8080'));

function tarefa(id, titulo, vencimentoEm, concluidaEm, responsavelNome,
  contatoId = null, oportunidadeId = null) {
  return { id, titulo, descricao: null, vencimentoEm, concluidaEm,
    responsavelId: null, responsavelLogin: null, responsavelNome,
    contatoId, oportunidadeId, criadaEm: '2026-08-10T12:00:00Z' };
}
function contato(id, nome, email, telefone, empresa, responsavelNome, criadoEm) {
  return { id, nome, email, telefone, empresa, observacoes: null,
    responsavelId: responsavelNome ? `user-${responsavelNome.toLocaleLowerCase('pt-BR').replaceAll(' ', '-')}` : null,
    responsavelLogin: responsavelNome?.split(' ')[0]?.toLocaleLowerCase('pt-BR') ?? null,
    responsavelNome, criadoEm };
}
function etapa(id, nome, posicao, ganho = false, perda = false) {
  return { id, nome, posicao, ganho, perda };
}
function oportunidade(id, titulo, etapaId, contatoId, valorCentavos, responsavelNome, status = 'OPEN') {
  return { id, funilId: funilComercial.id, etapaId, contatoId, titulo, valorCentavos, status,
    previsaoFechamento: '2026-08-31', motivoPerda: null,
    responsavelId: `user-${responsavelNome.toLocaleLowerCase('pt-BR').replaceAll(' ', '-')}`,
    responsavelLogin: responsavelNome.split(' ')[0].toLocaleLowerCase('pt-BR'), responsavelNome,
    criadaEm: '2026-08-01T12:00:00Z' };
}
function statusDaEtapa(etapaId) {
  if (etapaId === 'stage-won') return 'WON';
  if (etapaId === 'stage-lost') return 'LOST';
  return 'OPEN';
}
function conversa(id, contatoNome, contatoIdentificador, canalTipo, canalNome, canalIdentificador, status, atendenteNome, ultimaMensagemEm) {
  return { id, channelConnectionId: `channel-${id}`, canalTipo, canalNome, canalIdentificador,
    contatoNome, contatoIdentificador, status, atendenteId: atendenteNome ? `user-${id}` : null,
    atendenteNome, ultimaMensagemEm, venceEm: null, versao: 1 };
}
function mensagem(id, direcao, texto, status, autorNome, criadaEm) {
  return { id, direcao, tipoConteudo: 'TEXT', texto, status,
    autorId: autorNome ? userId : null, autorNome, criadaEm, versao: 1 };
}
function papel(id, codigo, nome, sistema, permissoes, descricao = null) {
  return { id, codigo, nome, descricao, sistema, ativo: true, permissoes,
    gerenciavel: !sistema, atribuicoes: sistema ? 1 : 0 };
}
function catalogoDePermissoes() {
  return ['contacts.read', 'contacts.write', 'deals.read', 'deals.write', 'tasks.read',
    'tasks.write', 'conversations.read', 'conversations.write', 'channels.read',
    'dashboard.read', 'reports.read'].map((codigo) => ({ codigo,
    delegavelNoTenant: true, delegavelNaEquipe: true, delegavelProprio: true }));
}
function nav(routeId, rotulo, grupo, ordem) { return { routeId, rotulo, grupo, ordem, visivel: true }; }
function json(response, body, status = 200) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}
function send(response, status) { response.writeHead(status); response.end(); }
async function readJson(request) {
  let raw = '';
  for await (const chunk of request) raw += chunk;
  return raw ? JSON.parse(raw) : {};
}
function webSocketFrame(text) {
  const payload = Buffer.from(text);
  if (payload.length < 126) return Buffer.concat([Buffer.from([0x81, payload.length]), payload]);
  const header = Buffer.alloc(4);
  header[0] = 0x81;
  header[1] = 126;
  header.writeUInt16BE(payload.length, 2);
  return Buffer.concat([header, payload]);
}
