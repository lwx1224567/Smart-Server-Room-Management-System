import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';
import { agentFixture } from '../../tests/platform/agent-test-fixture';

test('person profile form persists skills and biography', async ({ page }) => {
  const fixture = await mockConsole(page);
  await page.goto('/home');
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('button', { name: '编辑', exact: true }).first().click();
  const dialog = page.getByRole('dialog', { name: '编辑维保人员' });
  await dialog.getByRole('textbox', { name: '个人简介' }).fill('熟悉机柜散热维护');
  await dialog.locator('.el-select').click();
  await page.getByRole('option', { name: '制冷散热', exact: true }).click();
  await dialog.getByRole('textbox', { name: '个人简介' }).click();
  await dialog.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.locator('.settings')).toContainText('熟悉机柜散热维护');
  expect(fixture.users[1].skills).toEqual(['制冷散热']);
  await page.reload();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await expect(page.locator('.settings')).toContainText('制冷散热');
});

// Migrated from client orchestration: backend rules are covered by AssistantUnifiedAgentTest.
test('recommendation requires card confirmation; cancellation and history remain read only', async () => {
  const f = agentFixture(route => route === 'chat' ? { messages: [{ role: 'assistant', text: '服务端推荐维保甲；历史记录由服务端查询' }], pending: { kind: 'assign', token: 'assign-one', summary: '指派 WO-1 给维保甲' } } : { messages: [] });
  await f.conversation.enable('1'); await f.conversation.command('推荐维保人员');
  expect(f.state.messages.at(-1).text).toContain('维保甲'); expect(f.calls.some(c => c.route === 'confirm')).toBe(false);
  await f.conversation.dismiss(); expect(f.calls.at(-1)!.route).toBe('pending/dismiss');
  await f.conversation.command('查询历史'); expect(f.calls.at(-1)!.route).toBe('chat');
  expect(f.calls.some(c => c.route === 'confirm')).toBe(false); f.conversation.dispose();
});

test('order detail queries history using device identity', async ({ page }) => {
  const fixture = await mockConsole(page);
  fixture.orders.push({
    id: 'WO-HISTORY-1',
    title: '设备维修',
    device: 'A-01',
    roomId: '1',
    cabinetId: 'stable-a',
    deviceId: 'device-a',
    status: '已完成',
    owner: '维保甲',
    created: new Date().toISOString(),
    steps: [],
    cause: '风扇故障',
    process: '更换风扇',
    result: '恢复',
  });
  await page.route('**/api/v1/work-orders/history?**', (route) => {
    expect(new URL(route.request().url()).searchParams.get('deviceId')).toBe('device-a');
    return route.fulfill({ json: { code: 200, data: fixture.orders } });
  });
  await page.goto('/home?business=alarm&order=WO-HISTORY-1');
  await page.locator('.settings').getByRole('button', { name: '详情', exact: true }).click();
  await page.getByRole('button', { name: '查看此对象维修历史' }).click();
  await expect(page.getByLabel('对象维修历史')).toContainText('更换风扇');
});
