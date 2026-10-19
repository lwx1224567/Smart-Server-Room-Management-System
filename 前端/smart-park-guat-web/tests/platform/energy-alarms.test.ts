import { test, expect } from '@playwright/test';
import { isElectricalAlarm } from '../../src/views/home/cockpit/energy/energy-alarms';

test('electrical classification uses metrics independently of the affected asset type', () => {
  expect(isElectricalAlarm({ entityType: 'CABINET', metric: 'power' })).toBe(true);
  expect(isElectricalAlarm({ entityType: 'DEVICE', metric: 'current' })).toBe(true);
  expect(isElectricalAlarm({ entityType: 'ROOM', metric: 'voltage' })).toBe(true);
  expect(isElectricalAlarm({ entityType: 'POWER', metric: '功率' })).toBe(true);
  expect(isElectricalAlarm({ entityType: 'CABINET', metric: 'temperature' })).toBe(false);
  expect(isElectricalAlarm({ entityType: 'CABINET', metric: 'humidity' })).toBe(false);
  expect(isElectricalAlarm({})).toBe(false);
});
