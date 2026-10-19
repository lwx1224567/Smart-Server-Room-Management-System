import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHomeSceneControls } from '../../src/views/home/assistant/home-scene-controls';

test('explicit home selection, visit, cancel, resume and door reuse validated scene executor', async () => {
  let selected = '', mode = 'patrol', open = false, angle = 0;
  const device = () => ({ id: 'A-01', doorOpen: open, doorAngle: angle, locked: false });
  const scene = {
    getAgentState: () => ({ ready: true, selected, devices: [device()], patrol: { mode } }),
    select: (key: string) => { selected = key; },
    visit: async (key: string) => { selected = key; return { cancelled: false, deviceId: key }; },
    setDeviceDoor: (_key: string, value: boolean) => { open = value; angle = value ? -Math.PI * .61 : 0; },
    resumePatrol: () => { mode = 'patrol'; }, cancelPatrol: () => { mode = 'paused'; },
  };
  const controls = createHomeSceneControls(() => scene, key => ({ state: key === 'A-01' ? 'LINKED' : 'UNLINKED' }));
  expect(controls.select('missing')).toBe(false); expect(selected).toBe('');
  expect(controls.select('A-01')).toBe(true);
  for (const action of [
    { type: 'visit' as const, cabinetSceneModelKey: 'A-01' },
    { type: 'cancel' as const }, { type: 'resume' as const },
    { type: 'door' as const, cabinetSceneModelKey: 'A-01', open: true },
    { type: 'door' as const, cabinetSceneModelKey: 'A-01', open: false },
  ]) expect((await controls.execute(action)).status).toBe('SUCCEEDED');
  expect((await controls.execute({ type: 'visit', cabinetSceneModelKey: 'missing' })).status).toBe('FAILED');
  const unavailable = createHomeSceneControls(() => undefined, () => ({ state: 'LINKED' }));
  expect(unavailable.select('A-01')).toBe(false);
  expect((await unavailable.execute({ type: 'resume' })).code).toBe('SCENE_NOT_READY');
});

test('active home and Agent source graph excludes legacy assistant dependencies', () => {
  const base = resolve('src/views/home');
  for (const file of ['composables/useHomeDashboard.ts', 'index.vue', 'assistant/agent-assistant.ts',
    'assistant/home-scene-controls.ts', 'assistant/agent-conversation.ts', 'assistant/scene-action-executor.ts',
    'assistant/InspectionAssistant.vue', 'cockpit/alarms/components/InspectionAssistantCard.vue']) {
    const text = readFileSync(resolve(base, file), 'utf8');
    expect(text, file).not.toMatch(/assistant-service|assistant-model|monitoring-query|alarm-orders|createWorkflow|createAlarmOrders|setAssistantModelAdapter/);
  }
});
