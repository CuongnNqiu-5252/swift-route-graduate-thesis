import assert from 'node:assert/strict';
import test from 'node:test';
import { isRunnableFlow } from '../src/application/operation-flow-graph.ts';

const node = (step) => ({ id: step, data: { step } });
const edge = (source, target) => ({ source, target });

test('a Drive import can run only when preparation leads to import', () => {
  const nodes = ['import', 'schedule', 'prepare'].map(node);
  assert.equal(isRunnableFlow('import', nodes, [edge('schedule', 'prepare'), edge('prepare', 'import')]), true);
  assert.equal(isRunnableFlow('import', nodes, [edge('schedule', 'prepare')]), false);
  assert.equal(isRunnableFlow('import', nodes, [edge('schedule', 'import'), edge('import', 'prepare')]), false);
});

test('planning and import cannot be linked into one runnable flow', () => {
  assert.equal(isRunnableFlow('planning', ['schedule', 'plan'].map(node), [edge('schedule', 'plan')]), true);
  assert.equal(isRunnableFlow('planning', ['schedule', 'plan', 'import'].map(node),
    [edge('schedule', 'plan'), edge('plan', 'import')]), false);
});
