import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const verificador = join(aqui, 'verificar-dependencias.mjs');

function executar(relatorio) {
  return spawnSync(process.execPath, [verificador], {
    input: JSON.stringify(relatorio),
    encoding: 'utf8',
  });
}

test('aceita relatório completo sem vulnerabilidade', () => {
  const resultado = executar({
    auditReportVersion: 2,
    vulnerabilities: {},
    metadata: {
      vulnerabilities: {
        info: 0,
        low: 0,
        moderate: 0,
        high: 0,
        critical: 0,
        total: 0,
      },
    },
  });

  assert.equal(resultado.status, 0, resultado.stderr);
  assert.match(resultado.stdout, /Nenhuma vulnerabilidade alta ou crítica/);
});

test('reprova erro de transporte do npm audit', () => {
  const resultado = executar({
    error: {
      code: 'EAUDITENDPOINT',
      summary: 'audit endpoint returned an error',
    },
  });

  assert.equal(resultado.status, 1);
  assert.match(resultado.stderr, /não produziu um relatório completo/);
});

test('reprova JSON vazio para não confundir scanner ausente com resultado limpo', () => {
  const resultado = executar({});

  assert.equal(resultado.status, 1);
  assert.match(resultado.stderr, /falha fechado/);
});
