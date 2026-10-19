import { test, expect } from '@playwright/test';
import { createServer } from 'node:http';
import { mockConsole } from './console-fixture';

for (const interrupted of [false, true]) {
  test(`real stream renders before completion and ${interrupted ? 'marks interruption' : 'merges the final message once'}`, async ({
    page,
  }) => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let requestBody: any;
    const first = '第一段中文🙂。',
      second = '第二段继续解释。';
    const event = (type: string, data: unknown) =>
      `event: ${type}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`;
    const server = createServer(async (req, res) => {
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept, X-Assistant-Page');
      res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
      if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
      }
      const chunks: Buffer[] = [];
      for await (const chunk of req) chunks.push(Buffer.from(chunk));
      requestBody = JSON.parse(Buffer.concat(chunks).toString());
      res.writeHead(200, {
        'Content-Type': 'text/event-stream;charset=UTF-8',
        'Cache-Control': 'no-cache',
      });
      res.write(event('status', { message: '正在查询…' }) + event('message_start', {}));
      const bytes = Buffer.from(event('delta', { text: first }));
      const cut = bytes.indexOf(Buffer.from('🙂')) + 1;
      res.write(bytes.subarray(0, cut));
      setImmediate(() => res.write(bytes.subarray(cut)));
      await gate;
      if (!interrupted) {
        res.write(event('delta', { text: second }));
        res.write(
          event('done', {
            messages: [
              { role: 'user', text: '详细介绍巡检流程' },
              { role: 'assistant', text: first + second },
            ],
            reply: first + second,
          }),
        );
      }
      res.end();
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as { port: number }).port;
    try {
      await mockConsole(page);
      await page.route('**/api/v1/assistant/rooms/1/session', (route) =>
        route.fulfill({ json: { code: 200, data: { messages: [] } } }),
      );
      await page.route('**/api/v1/assistant/rooms/1/chat', (route) =>
        route.continue({ url: `http://127.0.0.1:${port}/sse` }),
      );
      await page.goto('/home');
      await page.evaluate(async () => {
        const vuePath = '/node_modules/.vite/deps/vue.js',
          servicePath = '/src/views/home/assistant/agent-assistant.ts',
          componentPath = '/src/views/home/assistant/InspectionAssistant.vue';
        const assistant = (await import(servicePath)).createAgentAssistant(() => undefined, { resolveCabinet: () => ({ state: "UNLINKED" }) });
        await assistant.enableAgent('1');
        (window as any).streamAssistant = assistant;
        const host = document.createElement('div');
        host.id = 'stream-test';
        host.style.cssText =
          'position:fixed;inset:10px auto 10px 10px;width:650px;z-index:99999;background:#082846;padding:10px';
        document.body.append(host);
        (await import(vuePath))
          .createApp((await import(componentPath)).default, { assistant })
          .mount(host);
      });
      const card = page.locator('#stream-test'),
        log = card.getByRole('log');
      await card.getByRole('textbox', { name: '巡检助手指令' }).fill('详细介绍巡检流程');
      await card.getByRole('button', { name: '发送', exact: true }).click();
      await expect(log).toContainText(first);
      await expect(log).not.toContainText(second);
      expect(await page.evaluate(() => (window as any).streamAssistant.state.dialogueBusy)).toBe(
        true,
      );
      expect(requestBody.stream).toBe(true);
      release();
      if (interrupted) {
        await expect(log).toContainText('回答传输中断');
        await expect(log).toContainText(first);
        await expect(log).not.toContainText(second);
      } else {
        await expect(log.getByText(first + second, { exact: true })).toHaveCount(1);
      }
      await expect
        .poll(() => page.evaluate(() => (window as any).streamAssistant.state.dialogueBusy))
        .toBe(false);
    } finally {
      release();
      server.closeAllConnections();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
}
