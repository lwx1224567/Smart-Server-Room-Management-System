import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
if (!process.env.CONSOLE_ADMIN_PASSWORD) throw new Error('需要通过环境变量提供管理员密码');
const browser = await chromium.launch({
  channel: 'chromium',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(process.env.CONSOLE_PREVIEW_URL ?? 'http://127.0.0.1:5177/login');
  await page
    .getByRole('textbox', { name: '用户名', exact: true })
    .fill(process.env.CONSOLE_ADMIN_USER ?? 'admin');
  await page
    .getByRole('textbox', { name: '密码', exact: true })
    .fill(process.env.CONSOLE_ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登 录' }).click();
  await page.waitForURL('**/home');
  await page.waitForFunction(
    () => document.querySelector('.weather')?.textContent?.includes('°C'),
    {},
    { timeout: 30000 },
  );
  await mkdir('test-results/console-preview', { recursive: true });
  await page.screenshot({ path: 'test-results/console-preview/admin.png' });
  console.log(
    JSON.stringify({
      weather: await page.locator('.weather').innerText(),
      updated: await page.locator('.weather').getAttribute('title'),
    }),
  );
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('dialog', { name: '管理员设置' }).waitFor();
  await page.screenshot({ path: 'test-results/console-preview/settings.png' });
  console.log(
    JSON.stringify({
      personnelRows: await page.locator('.el-table__body tbody tr').count(),
      pageErrors: errors,
    }),
  );
  await page.locator('.settings').getByRole('button', { name: '退出登录', exact: true }).click();
  await page.waitForURL('**/login');
  console.log('真实管理员登录、天气、设置查询和退出验证通过');
} finally {
  await browser.close();
}
