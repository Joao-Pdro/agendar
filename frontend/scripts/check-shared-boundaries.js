#!/usr/bin/env node
/**
 * Comprova que a regra de arquitetura está viva:
 * src/app/shared/ não pode importar de features/.
 *
 * Cria um arquivo temporário em shared/ que importa uma feature, roda o ESLint
 * e falha se o ESLint NÃO reprovar. Roda em Node (fora do Karma, que é browser).
 */
const { execFileSync } = require('node:child_process');
const { rmSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');

const projectRoot = join(__dirname, '..');
const probePath = join(
  projectRoot,
  'src/app/shared/utils',
  `__shared-import-probe-${process.pid}.ts`,
);

writeFileSync(
  probePath,
  [
    "import { AgendaPageComponent } from '../../features/scheduling/pages/agenda-page/agenda-page.component';",
    'export const probe = AgendaPageComponent;',
    '',
  ].join('\n'),
);

let exitCode = 0;
let output = '';

try {
  output = execFileSync('npx', ['eslint', '--no-color', probePath], {
    cwd: projectRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
} catch (error) {
  exitCode = error.status ?? 1;
  output = `${error.stdout ?? ''}${error.stderr ?? ''}`;
} finally {
  rmSync(probePath, { force: true });
}

if (exitCode === 0) {
  console.error(
    'FALHOU: o ESLint aceitou um import de shared/ para features/. A regra no-restricted-imports está quebrada.',
  );
  process.exit(1);
}

if (!output.includes('no-restricted-imports')) {
  console.error(
    `FALHOU: o eslint falhou, mas por outro motivo que não a regra no-restricted-imports.\n${output}`,
  );
  process.exit(1);
}

console.log('OK: shared/ importando de features/ é reprovado pelo ESLint.');
