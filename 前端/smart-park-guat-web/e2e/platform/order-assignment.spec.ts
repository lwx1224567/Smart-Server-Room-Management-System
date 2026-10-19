import { test, expect } from '@playwright/test';
import { mockConsole, loginAs } from './console-fixture';
import { agentFixture } from '../../tests/platform/agent-test-fixture';

test('completed order highlights repair and hides verbose records', async ({ page }) => {
  const fixture = await mockConsole(page);
  fixture.orders.push({
    id: 'WO-READABLE-1',
    title: 'A-01 高温告警',
    device: 'A-01',
    priority: '紧急',
    status: '已完成',
    owner: '李四',
    created: '2026-10-15T09:29:53Z',
    submittedAt: '2026-10-15T09:32:52Z',
    cause: '机柜风扇再次损坏',
    process: '更换风扇',
    result: '已成功解决',
    retestNote: '监测数据满足恢复阈值与持续时间，已归档',
    retestEvidence: {
      passed: true,
      checkedAt: '2026-10-15T09:33:46Z',
      evidence: [
        {
          metric: 'temperature',
          value: 24,
          unit: '℃',
          observedAt: '2026-10-15T09:33:40Z',
          source: 'ADMIN_DEMO_TEMPERATURE',
        },
      ],
    },
    steps: [
      '管理员创建工单',
      '2026-10-15T09:31:06Z · admin · assign → 李四',
      '2026-10-15T09:32:20Z · 李四 · start',
      '2026-10-15T09:32:51Z · 李四 · save',
      '2026-10-15T09:32:52Z · 李四 · submit · 原因：机柜风扇再次损坏',
      '2026-10-15T09:33:01Z · 小维自动复检 · 未收到本次提交后的对应对象有效监测数据',
      '2026-10-15T09:33:46Z · 小维自动复检 · 监测数据满足恢复阈值与持续时间，已归档',
    ],
  });
  await page.goto('/home?business=alarm&order=WO-READABLE-1');
  await page.locator('.settings').getByRole('button', { name: '详情', exact: true }).click();
  const detail = page.locator('.inline-order');
  await expect(detail.getByRole('status')).toContainText('复检通过，工单已完成');
  await expect(detail.getByRole('status')).toContainText('24 ℃');
  await expect(detail.locator('.repair-summary')).toContainText('更换风扇');
  await expect(detail.locator('.order-timeline')).toContainText('指派给 李四');
  await expect(detail.locator('.order-timeline')).toContainText('2026/10/15 17:31:06');
  await expect(detail.locator('.order-timeline')).not.toContainText('save');
  await expect(detail.getByText(/未收到本次提交后的/)).toBeHidden();
  await expect(detail.getByText(/ADMIN_DEMO_TEMPERATURE/)).toBeHidden();
  await detail.scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'test-results/order-detail-readable.png', animations: 'disabled' });
  await detail.getByText('查看详细记录', { exact: true }).click();
  await expect(detail.getByText(/未收到本次提交后的/)).toBeVisible();
  await expect(detail.getByText(/ADMIN_DEMO_TEMPERATURE/)).toBeVisible();
});

test('top work order entry assigns a shared order and maintainer submits cause and solution', async ({
  page,
}) => {
  const fixture = await mockConsole(page);
  fixture.orders.push({
    id: 'WO-ASSIGN-1',
    title: '机柜温度告警',
    device: 'A-01',
    source: '告警转工单',
    priority: '高',
    status: '待受理',
    owner: '未指派',
    created: new Date().toISOString(),
    steps: [],
    siteChecked: false,
    verified: false,
  });
  await page.goto('/home?business=alarm&order=WO-ASSIGN-1');
  await expect(page.locator('.settings .work-orders')).toBeVisible();
  await expect(page.getByRole('dialog', { name: '告警工单记录' })).toHaveCount(0);
  await expect(page.getByRole('dialog', { name: '工单详情' })).toHaveCount(0);
  await expect(page.locator('.inline-order')).toHaveCount(0);
  await page.locator('.settings').getByRole('button', { name: '详情', exact: true }).click();
  const adminDetail = page.locator('.inline-order');
  await adminDetail.getByRole('combobox', { name: '指派维保人员' }).selectOption('2');
  await adminDetail.getByRole('button', { name: '确认指派' }).click();
  await expect(adminDetail).toContainText('维保甲');
  expect(fixture.orders[0].ownerAccountId).toBe(2);
  await loginAs(page, 'MAINTAINER');
  await page.getByRole('button', { name: '工单中心', exact: false }).click();
  await expect(page.locator('tbody')).toContainText('机柜温度告警');
  await page.getByRole('button', { name: '详情', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '工单详情' });
  await detail.getByRole('button', { name: '开始处理', exact: true }).click();
  await detail.getByRole('textbox', { name: '故障原因' }).fill('风道堵塞');
  await detail.getByRole('textbox', { name: '处理过程及措施' }).fill('清理风道并检查风扇');
  await detail
    .getByRole('textbox', { name: '处理结果', exact: true })
    .fill('现场已恢复，等待数据复检');
  await detail.getByRole('button', { name: '已解决，提交小维复检' }).click();
  await expect(detail).toContainText('待复测');
  expect(fixture.orders[0].cause).toBe('风道堵塞');
  expect(fixture.orders[0].verified).toBe(false);
});

test('Xiaowei cannot assign through conversation or invented frontend action', async () => {
  const f = agentFixture(route => route === 'chat' ? { messages: [{ role: 'assistant', text: '请先选择真实告警和工单' }] } : { messages: [] });
  await f.conversation.enable('1'); await f.conversation.command('把 WO-1 指派给维保甲'); await f.conversation.command('确认'); await f.conversation.confirm();
  expect(f.calls.filter(c => c.route === 'chat')).toHaveLength(2); expect(f.calls.some(c => c.route === 'confirm')).toBe(false);
  expect(f.state.messages.at(-1).text).toContain('选择真实告警'); expect(f.conversation).not.toHaveProperty('setModelAdapter'); f.conversation.dispose();
});
