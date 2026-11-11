#!/usr/bin/env node
/**
 * Comprova que a regra de arquitetura está viva:
 * src/app/shared/ não pode importar de features/.
 *
 * O ESLint aplica a regra `no-restricted-imports` pelo caminho do arquivo
 * (config em `eslint.config.js`, bloco `src/app/shared/**`). Por isso a sonda
 * precisa estar DENTRO de `src/app/shared/` para ser reprovada — um arquivo em
 * `os.tmpdir()` seria avaliado pelas regras globais e passaria.
 *
 * Para não depender de uma pasta que pode estar vazia (o Git não versiona
 * diretório vazio), a sonda é criada dentro de `src/app/shared/` com
 * `mkdirSync(..., { recursive: true })`, que cria a pasta se precisar, e é
 * apagada no `finally`. Roda em Node porque o Karma é browser e não tem fs.
 */
const { execFileSync } = require('node:child_process');
const { mkdirSync, rmSync, writeFileSync } = require('node:fs');
const { join, relative, sep } = require('node:path');

const projectRoot = join(__dirname, '..');
const sharedRoot = join(projectRoot, 'src/app/shared');
const probePath = join(sharedRoot, `__shared-import-probe-${process.pid}.ts`);
const featuresPath = join(projectRoot, 'src/app/features/scheduling/scheduling.routes');

// Caminho relativo da sonda para a feature, para provar a importação inválida.
const relativeFeatures = relative(sharedRoot, featuresPath).split(sep).join('/');

let exitCode = 0;
let output = '';

try {
  mkdirSync(sharedRoot, { recursive: true });
  writeFileSync(
    probePath,
    [
      `import { SCHEDULING_ROUTES } from '${relativeFeatures}';`,
      'export const probe = SCHEDULING_ROUTES;',
      '',
    ].join('\n'),
  );

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
