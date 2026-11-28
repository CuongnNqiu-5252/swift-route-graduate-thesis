import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync, readdirSync } from 'node:fs';
import ts from 'typescript';

const source = new URL('../src/', import.meta.url);
const english = JSON.parse(readFileSync(new URL('i18n/messages.en.json', source), 'utf8'));

test('explicit UI messages have a non-empty English translation', () => {
  const missing = new Set();
  for (const path of readdirSync(source, { recursive: true })) {
    if (!path.endsWith('.tsx')) continue;
    const file = ts.createSourceFile(
      path,
      readFileSync(new URL(path.replaceAll('\\', '/'), source), 'utf8'),
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TSX,
    );

    function visit(node) {
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)
          && node.expression.text === 't' && node.arguments.length > 0) {
        const message = node.arguments[0];
        if (ts.isStringLiteral(message) && !english[message.text]?.trim()) {
          missing.add(message.text);
        }
      }
      ts.forEachChild(node, visit);
    }

    visit(file);
  }

  assert.deepEqual([...missing], []);
});
