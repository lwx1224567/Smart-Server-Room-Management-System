import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

test('business menu keeps address, refresh and browser history in sync', async ({ page }) => {
  await mockConsole(page);
  await page.goto('/home?business=alarm');
  const menu = page.getByRole('navigation', { name: '园区业务菜单' });
  for (const [name, key] of [
    ['能效管理', 'energy'],
    ['机房运维', 'operations'],
    ['资产总览', 'assets'],
    ['网络拓扑', 'topology'],
  ]) {
    await menu.getByRole('button', { name, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`business=${key}$`));
    await expect(menu.getByRole('button', { name, exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  }
  await page.reload();
  await expect(menu.getByRole('button', { name: '网络拓扑', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.goBack();
  await expect(page).toHaveURL(/business=assets$/);
  await expect(menu.getByRole('button', { name: '资产总览', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.goto('/home');
  await expect(menu.getByRole('button', { name: '机房运维', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});
