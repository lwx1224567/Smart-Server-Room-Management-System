import { test, expect } from '@playwright/test';
import {
  originLabel,
  runtimeLabel,
  readingLabel,
  uPositionLabel,
  registrationLabel,
} from '../../src/utils/platform-display';

test('persisted DEMO and REAL assets do not imply a measured runtime state', () => {
  expect(originLabel({ recordOrigin: 'DEMO' })).toBe('真实数据');
  expect(originLabel({ recordOrigin: 'REAL' })).toBe('真实数据');
  expect(originLabel()).toBe('来源未确认');
  expect(runtimeLabel('UNKNOWN')).toBe('未采集');
  expect(registrationLabel('ACTIVE')).toBe('登记有效');
});
test('null is missing while numeric zero remains a real value', () => {
  expect(readingLabel(null)).toBe('--');
  expect(readingLabel(undefined)).toBe('--');
  expect(readingLabel(0)).toBe('0');
  expect(readingLabel(1.25)).toBe('1.25');
  expect(uPositionLabel(null, null)).toBe('未登记');
  expect(uPositionLabel(5, 2)).toBe('5U · 2U高');
});
