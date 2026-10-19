import { test, expect } from '@playwright/test';
import { mockConsole, loginAs } from './console-fixture';

test('maintenance layout fits viewport and keeps long content scrollable inside main', async ({
  page,
}) => {
  await mockConsole(page, 'MAINTAINER');
  await page.goto('/maintenance');
  for (const viewport of [
    { width: 1920, height: 924 },
    { width: 1366, height: 768 },
    { width: 1024, height: 768 },
    { width: 375, height: 812 },
  ]) {
    await page.setViewportSize(viewport);
    await expect(page.locator('.maintenance-sidebar')).toBeVisible();
    await expect
      .poll(() =>
        page.evaluate(() => ({
          horizontal: document.documentElement.scrollWidth > innerWidth,
          vertical: document.documentElement.scrollHeight > innerHeight,
        })),
      )
      .toEqual({ horizontal: false, vertical: false });
    const main = page.locator('.maintenance-content main');
    await expect
      .poll(() => main.evaluate((element) => element.scrollWidth <= element.clientWidth))
      .toBe(true);
    await expect(
      page.locator('.pageTop').getByRole('button', { name: '退出登录', exact: true }),
    ).toBeInViewport();
    await page.screenshot({
      path: `test-results/maintenance-${viewport.width}x${viewport.height}.png`,
    });
  }
  await page.getByRole('button', { name: '个人信息与密码', exact: true }).click();
  await expect(page.locator('.personal-account')).toBeVisible();
  await page.locator('.maintenance-content main').evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  await expect(page.locator('.personal-account').getByRole('button')).toBeInViewport();
});

test('dedicated maintenance backend preserves handling records and administrator sees progress in alarms', async ({
  page,
}) => {
  const fixture = await mockConsole(page, 'MAINTAINER');
  fixture.orders.push({
    id: 'WO-INTEGRATION-1',
    title: '小维温度巡检工单',
    device: 'A-02',
    alarmEvent: 'alarm-1',
    source: '告警转工单',
    recordOrigin: 'DEMO',
    priority: '高',
    status: '待受理',
    owner: '未指派',
    created: new Date().toISOString(),
    note: '检查风道',
    steps: ['小维创建工单'],
    siteChecked: false,
    verified: false,
  });
  await page.goto('/login');
  await loginAs(page, 'MAINTAINER');
  await expect(page.locator('.maintenance-sidebar')).toContainText('维保人员后台');
  await expect(page.locator('.summary-grid')).toContainText('待领取工单');
  await expect(page.locator('.pageTop')).toContainText('智慧机房管控系统');
  await page.goto('/weibao');
  await expect(page).toHaveURL(/\/maintenance$/);
  await page.getByRole('button', { name: '工单中心', exact: false }).click();
  await page.getByRole('button', { name: '待领取工单', exact: true }).click();
  await page.getByRole('button', { name: '详情', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '工单详情' });
  await detail.getByRole('button', { name: '领取工单', exact: true }).click();
  await detail.getByRole('button', { name: '开始处理', exact: true }).click();
  await detail.getByRole('textbox', { name: '故障原因' }).fill('滤网堵塞');
  await detail.getByRole('textbox', { name: '处理过程及措施' }).fill('清理滤网，检查送风通道');
  await detail
    .getByRole('textbox', { name: '处理结果', exact: true })
    .fill('已清理，等待真实温度复测');
  await detail.getByRole('button', { name: '已解决，提交小维复检', exact: true }).click();
  await expect(detail).toContainText('待复测');
  await detail.getByRole('button', { name: 'Close this dialog' }).click();
  await page.getByRole('button', { name: '处理记录', exact: true }).first().click();
  await expect(page.locator('tbody')).toContainText('小维温度巡检工单');
  await page.reload();
  await expect(page.locator('tbody')).toContainText('待复测');
  await page.getByRole('button', { name: '详情', exact: true }).click();
  await expect(detail).toContainText('清理滤网，检查送风通道');
  await detail.getByRole('button', { name: 'Close this dialog' }).click();
  await page.getByRole('button', { name: '个人信息与密码', exact: true }).click();
  await expect(page.locator('.personal-account')).toContainText('maint_test');
  await page.locator('.pageTop').getByRole('button', { name: '退出登录', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await loginAs(page, 'ADMIN');
  await page.goto('/home?business=alarm&order=WO-INTEGRATION-1');
  await expect(page.locator('.settings .work-orders')).toBeVisible();
  await expect(page.getByRole('dialog', { name: '告警工单记录' })).toHaveCount(0);
  await page.locator('.settings').getByRole('button', { name: '详情', exact: true }).click();
  const adminDetail = page.locator('.inline-order');
  await expect(adminDetail).toContainText('待复测');
  await adminDetail.getByRole('button', { name: '小维复测', exact: true }).click();
  await expect(adminDetail).toContainText('真实监测数据接口未接入');
  expect(fixture.orders[0].status).toBe('待复测');
});
