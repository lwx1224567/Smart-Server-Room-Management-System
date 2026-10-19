import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { parse } from '@vue/compiler-sfc';

const root = path.resolve(import.meta.dirname, '..');
const norm = (p) => p.replaceAll('\\', '/');
const rel = (p) => norm(path.relative(root, p));
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });
}
const files = [
  ...walk(path.join(root, 'src')),
  ...walk(path.join(root, 'public')),
  path.join(root, 'index.html'),
];
const set = new Set(files);
const graph = new Map();
const details = [];
const unresolved = [];
function resolve(from, spec) {
  spec = spec.replace(/[?#].*$/, '');
  if (!spec.startsWith('.') && !spec.startsWith('@/') && !spec.startsWith('/')) return [];
  const p = spec.startsWith('@/')
    ? path.join(root, 'src', spec.slice(2))
    : spec.startsWith('/')
      ? path.join(root, 'public', spec.slice(1))
      : path.resolve(path.dirname(from), spec);
  if (spec.includes('*') || spec.includes('${')) {
    const pattern = norm(p)
      .replace(/[.+?^$()|[\]\\]/g, '\\$&')
      .replaceAll('*', '.*')
      .replace(/\\\$\{[^}]+\}/g, '[^/]+');
    return files.filter((f) => new RegExp('^' + pattern + '$').test(norm(f)));
  }
  return ['', '.ts', '.js', '.vue', '.scss', '.css', '/index.ts', '/index.js', '/index.vue']
    .map((ext) => path.resolve(p + ext))
    .filter((f) => set.has(f));
}
function add(from, spec, kind) {
  const targets = resolve(from, spec);
  for (const to of targets) {
    graph.get(from).add(to);
    details.push({ from: rel(from), to: rel(to), kind, spec });
  }
  if (!targets.length && (spec.startsWith('@/') || spec.startsWith('.')))
    unresolved.push({ from: rel(from), spec, kind });
}
for (const file of files) {
  graph.set(file, new Set());
  if (!/\.(?:vue|ts|js|scss|css|html)$/.test(file) || file.endsWith('.d.ts')) continue;
  const text = fs.readFileSync(file, 'utf8');
  let code = /\.(?:ts|js)$/.test(file) ? text : '';
  if (file.endsWith('.vue')) {
    const { descriptor: d } = parse(text);
    code = [d.script?.content, d.scriptSetup?.content].filter(Boolean).join('\n');
    for (const b of [d.script, d.scriptSetup, ...d.styles])
      if (b?.src) add(file, b.src, 'block-src');
  }
  const ast = ts.createSourceFile(file, code, ts.ScriptTarget.Latest, true);
  function visit(n) {
    if (
      (ts.isImportDeclaration(n) || ts.isExportDeclaration(n)) &&
      n.moduleSpecifier &&
      ts.isStringLiteral(n.moduleSpecifier)
    )
      add(
        file,
        n.moduleSpecifier.text,
        n.importClause?.isTypeOnly || n.isTypeOnly ? 'type' : 'import',
      );
    if (
      ts.isCallExpression(n) &&
      (n.expression.kind === ts.SyntaxKind.ImportKeyword ||
        n.expression.getText(ast) === 'import.meta.glob')
    ) {
      const arg = n.arguments[0];
      if (arg && (ts.isStringLiteral(arg) || ts.isNoSubstitutionTemplateLiteral(arg)))
        add(file, arg.text, 'dynamic');
      else if (arg && ts.isTemplateExpression(arg))
        add(file, arg.getText(ast).slice(1, -1), 'dynamic-template');
    }
    ts.forEachChild(n, visit);
  }
  visit(ast);
  for (const match of text.matchAll(
    /(?:src|href)\s*=\s*["']([^"']+)["']|url\(\s*["']?([^)'"\s]+)|["'](\/videos\/[^"']+)["']/g,
  ))
    add(file, match[1] ?? match[2] ?? match[3], 'asset');
}
const reached = new Set();
function reach(f) {
  if (reached.has(f)) return;
  reached.add(f);
  for (const to of graph.get(f) ?? []) reach(to);
}
reach(path.join(root, 'index.html'));
// HTML entry uses /src/main.ts, which is a source entry rather than a public resource.
reach(path.join(root, 'src/main.ts'));
const unused = files
  .filter((f) => !reached.has(f) && !f.endsWith('.d.ts') && !/\.(md|html)$/.test(f))
  .map((f) => ({
    file: rel(f),
    bytes: fs.statSync(f).size,
    incoming: details.filter((e) => e.to === rel(f)),
  }));
const sourceFiles = files.filter(
  (f) =>
    reached.has(f) &&
    /\.(ts|js|vue)$/.test(f) &&
    !f.endsWith('.d.ts') &&
    !f.includes(`${path.sep}vendor${path.sep}`),
);
const combined = sourceFiles.map((f) => fs.readFileSync(f, 'utf8')).join('\n');
const apiMethods = [];
for (const file of ['src/api/platform/integrations.ts', 'src/api/platform/inventory.ts']) {
  const ast = ts.createSourceFile(
    file,
    fs.readFileSync(path.join(root, file), 'utf8'),
    ts.ScriptTarget.Latest,
    true,
  );
  function visit(n) {
    if (
      ts.isVariableDeclaration(n) &&
      n.initializer &&
      ts.isObjectLiteralExpression(n.initializer)
    ) {
      for (const prop of n.initializer.properties) {
        const object = n.name.getText(ast),
          method = prop.name?.getText(ast);
        if (!method) continue;
        const pattern = new RegExp('\\b' + object + '\\s*\\.\\s*' + method + '\\b', 'g');
        apiMethods.push({ file, object, method, used: pattern.test(combined) });
      }
    }
    ts.forEachChild(n, visit);
  }
  visit(ast);
}
const exportedOnce = [];
for (const file of sourceFiles.filter((f) => /\.(ts|js)$/.test(f))) {
  const ast = ts.createSourceFile(
    file,
    fs.readFileSync(file, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
  );
  for (const statement of ast.statements) {
    if (!statement.modifiers?.some((m) => m.kind === ts.SyntaxKind.ExportKeyword)) continue;
    const names = ts.isVariableStatement(statement)
      ? statement.declarationList.declarations.map((d) => d.name.getText(ast))
      : statement.name
        ? [statement.name.getText(ast)]
        : [];
    for (const name of names)
      if (
        /^\w+$/.test(name) &&
        [...combined.matchAll(new RegExp('\\b' + name + '\\b', 'g'))].length === 1
      )
        exportedOnce.push({ file: rel(file), name, kind: ts.SyntaxKind[statement.kind] });
  }
}
const result = {
  files: files.length,
  reached: reached.size,
  unused,
  unresolved,
  details,
  apiMethods,
  exportedOnce,
};
if (process.argv.includes('--json')) console.log(JSON.stringify(result, null, 2));
else
  console.log(
    JSON.stringify(
      {
        files: result.files,
        reached: result.reached,
        unused: result.unused,
        unresolved,
        apiMethods,
        exportedOnce,
      },
      null,
      2,
    ),
  );
