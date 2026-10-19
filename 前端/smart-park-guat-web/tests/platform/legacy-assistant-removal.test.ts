import { test, expect } from '@playwright/test';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, extname } from 'node:path';

test('retired modules are absent and executable sources have no static or dynamic references', () => {
  const retired = ['assistant-intent.js', 'workflow.js', 'alarm-orders.ts', 'monitoring-query.ts',
    'assistant-model.ts', 'assistant-service.js', 'assistant-service.d.ts'];
  for (const name of retired) expect(existsSync(resolve('src/views/home/assistant', name)), name).toBe(false);
  const stems = retired.map(name => name.replace(/\.(?:d\.ts|ts|js)$/, ''));
  const patterns = stems.map(stem => new RegExp(`(?:^['"\x60]|/)${stem}(?:\\.(?:js|ts|d\\.ts))?['"\x60]$`));
  const references: string[] = [];
  function scan(dir: string) {
    for (const item of readdirSync(dir, { withFileTypes: true })) {
      const file = resolve(dir, item.name);
      if (item.isDirectory()) scan(file);
      else if (['.ts', '.js', '.mjs', '.vue'].includes(extname(file))) {
        const text = readFileSync(file, 'utf8');
        // Path literals include imports, require, variable dynamic imports and script URLs.
        const literals = text.match(/['"`][^'"`\n]*['"`]/g) || [];
        for (const literal of literals) if (patterns.some(pattern => pattern.test(literal)))
          references.push(`${file}: ${literal}`);
      }
    }
  }
  for (const dir of ['src', 'scripts', 'e2e']) scan(resolve(dir));
  expect(references).toEqual([]);
});
