import { test, expect } from '@playwright/test';
import { normalizeDataSource } from '../../src/utils/unified-data-source';

test('legacy snapshots use one classification while preserving source and repair content', () => {
  const original = {
    recordOrigin: 'DEMO',
    source: 'MOCK_IMPORT',
    cause: 'DEMO 设备检查',
    children: [{ record_origin: 'DEMO' }, { mode: 'MANUAL' }, { recordOrigin: 'REAL' }],
  };
  const result = normalizeDataSource(original);
  expect(result.recordOrigin).toBe('REAL');
  expect(result.children).toEqual([
    { record_origin: 'REAL' },
    { mode: 'REAL' },
    { recordOrigin: 'REAL' },
  ]);
  expect(result.source).toBe(original.source);
  expect(result.cause).toBe(original.cause);
  expect(original.recordOrigin).toBe('DEMO');
});
