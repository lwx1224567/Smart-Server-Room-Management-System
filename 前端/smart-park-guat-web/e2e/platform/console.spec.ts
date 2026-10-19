import { test, expect } from '@playwright/test';
import { mockConsole, loginAs } from './console-fixture';

test('maintenance session survives reload, role guard and logout clear access', async ({
  page,
}) => {
  await mockConsole(page, 'MAINTAINER');
  await page.goto('/home');
  await expect(page).toHaveURL(/\/maintenance$/);
  await expect(page.locator('.maintenance-sidebar')).toContainText('维保人员后台');
  await page.screenshot({ path: 'test-results/maintenance-console.png' });
  await page.reload();
  await expect(page.locator('.maintenance-sidebar')).toBeVisible();
  await page.locator('.pageTop').getByRole('button', { name: '退出登录', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/maintenance');
  await expect(page).toHaveURL(/\/login$/);
  await loginAs(page, 'ADMIN');
  await page.locator('.pageTop').getByRole('button', { name: '退出登录', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => sessionStorage.getItem('smart-room-session'))).toBeNull();
});
test('Shanghai weather, read filters and delete only read notifications', async ({ page }) => {
  await mockConsole(page, 'ADMIN');
  await page.goto('/home');
  await expect(page.locator('.weather')).toContainText('上海 多云 27°C');
  await expect(page.locator('.weather')).toHaveAttribute('title', /更新于/);
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.locator('.settings').getByRole('button', { name: '消息中心', exact: false }).click();
  const drawer = page.getByRole('dialog', { name: '管理员设置' });
  await expect(drawer).toContainText('系统消息');
  await expect(page.getByRole('dialog', { name: '通知中心' })).toHaveCount(0);
  const panel = drawer.locator('.console-settings-dialog');
  await expect(panel).toBeVisible();
  await expect(page.locator('.el-drawer')).toHaveCount(0);
  const viewport = page.viewportSize()!;
  await expect
    .poll(async () => {
      const bounds = await panel.boundingBox();
      if (!bounds) return Infinity;
      return Math.max(
        Math.abs(bounds.x + bounds.width / 2 - viewport.width / 2),
        Math.abs(bounds.y + bounds.height / 2 - viewport.height / 2),
      );
    })
    .toBeLessThan(4);
  await page.screenshot({ path: 'test-results/notification-center-dialog.png' });
  await drawer.getByText('未读', { exact: true }).first().click();
  await expect(drawer).not.toContainText('系统消息');
  await drawer.getByText('全部', { exact: true }).click();
  await drawer.getByRole('button', { name: '一键删除所有已读消息' }).click();
  await expect(drawer).not.toContainText('系统消息');
  await expect(drawer).toContainText('请处理设备告警');
  await drawer.getByRole('button', { name: '标记已读' }).click();
  await drawer.getByText('未读', { exact: true }).first().click();
  await expect(drawer).toContainText('暂无消息');
});
test('admin creates edits searches deletes personnel and changes own password', async ({
  page,
}) => {
  await mockConsole(page, 'MAINTAINER');
  await page.goto('/maintenance');
  await loginAs(page, 'ADMIN');
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('button', { name: '新增维保人员', exact: true }).click();
  await page.getByRole('textbox', { name: '维保账号' }).fill('new_maint');
  await page.getByRole('textbox', { name: '维保姓名' }).fill('测试维保');
  await page.getByRole('textbox', { name: '维保密码' }).fill('Password123!');
  await page.getByRole('textbox', { name: '维保电话' }).fill('13800000000');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.locator('.el-table')).toContainText('测试维保');
  await page.getByRole('textbox', { name: '搜索维保人员' }).fill('测试维保');
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await page.getByRole('textbox', { name: '维保姓名' }).fill('改名维保');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await page.getByRole('textbox', { name: '搜索维保人员' }).fill('改名维保');
  await expect(page.locator('.el-table')).toContainText('改名维保');
  await page.getByRole('button', { name: '删除', exact: true }).click();
  await page.getByRole('button', { name: '确定', exact: true }).click();
  await expect(page.locator('.el-table')).not.toContainText('改名维保');
  await page.getByRole('button', { name: '管理员基本信息', exact: true }).click();
  await expect(page.locator('.settings')).toContainText('admin_test');
  await page.getByRole('textbox', { name: '原密码', exact: true }).fill('AdminTest123!');
  await page.getByRole('textbox', { name: '新密码', exact: true }).fill('NewPassword123!');
  await page.getByRole('textbox', { name: '确认新密码', exact: true }).fill('NewPassword123!');
  await page.getByRole('button', { name: '保存密码并重新登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
});
