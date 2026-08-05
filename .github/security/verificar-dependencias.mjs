/**
 * Reprova o build quando há vulnerabilidade alta ou crítica sem exceção
 * vigente registrada.
 *
 * Lê o JSON do `npm audit` pela entrada padrão. Sem dependências próprias, de
 * propósito: um verificador de supply chain que arrasta pacotes novos amplia
 * exatamente a superfície que deveria vigiar.
 *
 * Sai com 1 quando encontra vulnerabilidade não excetuada OU quando uma
 * exceção venceu. O segundo caso é tão importante quanto o primeiro: exceção
 * sem prazo vira permissão permanente, e ninguém percebe.
 *
 * Uso: npm audit --json | node .github/security/verificar-dependencias.mjs
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const SEVERIDADES_QUE_REPROVAM = new Set(['high', 'critical']);
const aqui = dirname(fileURLToPath(import.meta.url));

function lerEntradaPadrao() {
  try {
    return readFileSync(0, 'utf8');
  } catch {
    return '';
  }
}

function hoje() {
  // Data em UTC para não depender do fuso do runner.
  return new Date().toISOString().slice(0, 10);
}

const bruto = lerEntradaPadrao().trim();
if (!bruto) {
  console.error('Nenhuma saída de `npm audit` chegou pela entrada padrão.');
  process.exit(1);
}

let auditoria;
try {
  auditoria = JSON.parse(bruto);
} catch (erro) {
  console.error('Saída de `npm audit` não é JSON válido:', erro.message);
  process.exit(1);
}

// O npm também devolve JSON quando o registry está indisponível, mas esse JSON
// contém `error` e não é um relatório. Tratar `{}` ou erro de transporte como
// "zero vulnerabilidades" faria o gate aprovar exatamente quando o scanner não
// conseguiu trabalhar. A forma válida atual tem versão e totais explícitos.
if (
  auditoria.error
  || typeof auditoria.auditReportVersion !== 'number'
  || !auditoria.metadata
  || !auditoria.metadata.vulnerabilities
) {
  console.error(
    'O `npm audit` não produziu um relatório completo. '
      + 'A auditoria falha fechado; verifique registry e conectividade.',
  );
  process.exit(1);
}

const arquivoExcecoes = join(aqui, 'excecoes-de-dependencia.json');
const { excecoes = [] } = JSON.parse(readFileSync(arquivoExcecoes, 'utf8'));

const data = hoje();
const vencidas = excecoes.filter((e) => e.expira_em < data);
const vigentes = new Map(
  excecoes.filter((e) => e.expira_em >= data).map((e) => [e.id, e]),
);

// Um aviso chega em `via` como objeto com url do advisory; é de lá que sai o
// identificador estável (GHSA-...). O nome do pacote sozinho não serve: o
// mesmo pacote pode ganhar um aviso novo, e aí a exceção antiga o esconderia.
//
// Quando `via` traz uma string, ela é o NOME de outro pacote vulnerável: o
// npm marca assim quem só é afetado por depender de outro. Seguimos a cadeia,
// porque a exceção pertence ao advisory de origem — sem isso, aceitar uma
// falha exigiria repetir a mesma exceção para cada pacote que a arrasta.
function identificadores(vulnerabilidade, vulnerabilidades, visitados = new Set()) {
  const ids = new Set();
  if (visitados.has(vulnerabilidade.name)) return ids;
  visitados.add(vulnerabilidade.name);

  for (const via of vulnerabilidade.via ?? []) {
    if (typeof via === 'object' && via.url) {
      const encontrado = via.url.match(/(GHSA-[\w-]+|CVE-[\d-]+)/);
      if (encontrado) ids.add(encontrado[1]);
    } else if (typeof via === 'string' && vulnerabilidades[via]) {
      for (const id of identificadores(vulnerabilidades[via], vulnerabilidades, visitados)) {
        ids.add(id);
      }
    }
  }
  return ids;
}

const naoExcetuadas = [];
const excetuadas = [];

const vulnerabilidades = auditoria.vulnerabilities ?? {};

for (const [nome, v] of Object.entries(vulnerabilidades)) {
  if (!SEVERIDADES_QUE_REPROVAM.has(v.severity)) continue;

  const ids = identificadores(v, vulnerabilidades);
  const cobertura = [...ids].find((id) => vigentes.has(id));

  if (cobertura) {
    excetuadas.push({ nome, id: cobertura, severidade: v.severity });
  } else {
    naoExcetuadas.push({
      nome,
      severidade: v.severity,
      ids: ids.size ? [...ids].join(', ') : '(sem identificador de advisory)',
    });
  }
}

const totais = auditoria.metadata.vulnerabilities;
console.log(`Auditoria npm: ${JSON.stringify(totais)}`);

for (const e of excetuadas) {
  const excecao = vigentes.get(e.id);
  console.log(
    `  aceita  ${e.severidade.padEnd(8)} ${e.nome} (${e.id}) ` +
      `— responsável ${excecao.responsavel}, expira em ${excecao.expira_em}`,
  );
}

let reprovou = false;

for (const e of vencidas) {
  console.error(
    `EXCEÇÃO VENCIDA: ${e.id} (${e.pacote}) expirou em ${e.expira_em}. ` +
      'Corrija a vulnerabilidade ou reavalie e renove o prazo por decisão explícita.',
  );
  reprovou = true;
}

for (const e of naoExcetuadas) {
  console.error(
    `VULNERABILIDADE SEM EXCEÇÃO: ${e.severidade} em ${e.nome} [${e.ids}]`,
  );
  reprovou = true;
}

if (reprovou) {
  console.error(
    '\nPara aceitar conscientemente, acrescente uma entrada com responsável, ' +
      'prazo e fundamento em .github/security/excecoes-de-dependencia.json.',
  );
  process.exit(1);
}

console.log('Nenhuma vulnerabilidade alta ou crítica sem exceção vigente.');
