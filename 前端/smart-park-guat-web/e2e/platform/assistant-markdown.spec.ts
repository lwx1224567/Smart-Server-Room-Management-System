import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

test('assistant renders Markdown while user text and raw HTML stay literal', async ({ page }) => {
  await mockConsole(page);
  await page.goto('/home');
  await page.evaluate(async () => {
    const vuePath = '/node_modules/.vite/deps/vue.js',
      servicePath = '/src/views/home/assistant/agent-assistant.ts',
      componentPath = '/src/views/home/assistant/InspectionAssistant.vue';
    const assistant = (await import(servicePath)).createAgentAssistant(() => undefined, { resolveCabinet: () => ({ state: "UNLINKED" }) });
    assistant.state.messages.splice(
      0,
      assistant.state.messages.length,
      { role: 'user', text: '**我的问题**' },
      {
        role: 'assistant',
        text: '**查数据**\n\n- 查询告警\n- 查询工单\n\n**推流程**\n\n1. 创建工单\n2. 确认指派\n\n使用 `A-01`。\n\n```text\n**代码保持原样**\n```\n\n<script>window.markdownExecuted=true</script>\n<img src=x onerror="window.markdownExecuted=true">\n\n[正常链接](https://example.com) [危险链接](javascript:alert(1))',
      },
    );
    (window as any).markdownAssistant = assistant;
    const host = document.createElement('div');
    host.id = 'markdown-test';
    host.style.cssText =
      'position:fixed;inset:10px auto 10px 10px;width:650px;z-index:99999;background:#082846;padding:10px';
    document.body.append(host);
    (await import(vuePath))
      .createApp((await import(componentPath)).default, { assistant })
      .mount(host);
  });
  const card = page.locator('#markdown-test'),
    reply = card.locator('article.assistant .markdown-body');
  await expect(reply.locator('strong').first()).toHaveText('查数据');
  expect(
    await reply
      .locator('strong')
      .first()
      .evaluate((el) => getComputedStyle(el).fontWeight),
  ).toBe('700');
  await expect(reply.locator('ul > li')).toHaveCount(2);
  await expect(reply.locator('ol > li')).toHaveCount(2);
  await expect(reply.locator('pre code')).toHaveText('**代码保持原样**\n');
  await expect(card.locator('article.user')).toContainText('**我的问题**');
  await expect(card.locator('article.user strong')).toHaveCount(0);
  await expect(reply.locator('script, img')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).markdownExecuted)).toBeUndefined();
  await expect(reply.locator('a[href^="javascript:"]')).toHaveCount(0);
  await expect(reply.getByRole('link', { name: '正常链接' })).toHaveAttribute(
    'rel',
    'noopener noreferrer',
  );
  await page.evaluate(() => {
    (window as any).markdownAssistant.state.messages[1].text = '开头 **流式';
  });
  await expect(reply).toContainText('开头');
  await page.evaluate(() => {
    (window as any).markdownAssistant.state.messages[1].text += '加粗**\n\n- 第一项\n- 第二项';
  });
  await expect(reply.locator('strong')).toHaveText('流式加粗');
  await expect(reply.locator('ul > li')).toHaveCount(2);
  await expect(reply).not.toContainText('**');
  await card.evaluate((el) => {
    (el as HTMLElement).style.width = '360px';
  });
  await page.evaluate(() => {
    (window as any).markdownAssistant.state.messages[1].text =
      '共有 8 条活动告警。\n\n' +
      Array.from(
        { length: 8 },
        (_, i) =>
          `---\n\n**${i + 1}. 上联端口丢包偏高**\nA区06号机柜（A-06） · 重要 · 未处理\n指标：丢包率\n实测：2.4 %\n阈值：≤ 1 %\n告警ID：e5a2346f-8147-4e04-aeb1-9b8c008c132${i}`,
      ).join('\n\n') +
      '\n\n请回复“选择第几个”。';
  });
  await expect(reply.locator('strong')).toHaveCount(8);
  await expect(reply.locator('table')).toHaveCount(0);
  expect(await reply.evaluate((el) => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
});
