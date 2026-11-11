#!/usr/bin/env node
/**
 * Comprova que só `core/http` fala com `HttpClient`.
 *
 * - Nenhum arquivo de `features/` pode importar `HttpClient` (fora de testes).
 * - Nenhum arquivo de `shared/` pode importar `HttpClient`.
 *
 * Roda em Node porque o Karma não tem acesso ao filesystem do projeto.
 */
const { readdirSync, readFileSync, statSync } = require('node:fs');
const { join, relative } = require('node:path');

const projectRoot = join(__dirname, '..');
const srcApp = join(projectRoot, 'src/app');
const allowedDir = join(srcApp, 'core/http');

const IMPORT_PATTERN = /import[^;]*\bHttpClient\b[^;]*from\s+['"]@angular\/common\/http['"]/;

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full));
    } else if (entry.endsWith('.ts')) {
      out.push(full);
    }
  }
  return out;
}

const violations = [];

for (const file of walk(srcApp)) {
  const path = relative(srcApp, file);
  if (file.startsWith(allowedDir) || path.endsWith('.spec.ts')) {
    continue;
  }
  const source = readFileSync(file, 'utf8');
  if (IMPORT_PATTERN.test(source)) {
    violations.push(path);
  }
}

if (violations.length > 0) {
  console.error(
    'FALHOU: HttpClient só pode ser usado em core/http. Injete ApiClient nos outros lugares.\n' +
      violations.map((v) => `  - ${v}`).join('\n'),
  );
  process.exit(1);
}

console.log('OK: nenhum arquivo fora de core/http importa HttpClient.');
