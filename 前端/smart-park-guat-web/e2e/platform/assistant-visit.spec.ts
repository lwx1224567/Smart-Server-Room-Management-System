import { test, expect } from '@playwright/test';
import { mockConsole } from './console-fixture';

for (const outcome of ['arrived', 'cancelled', 'unavailable']) {
  test(`visit waits for actual patrol completion before querying: ${outcome}`, async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await mockConsole(page);
    const receipts: any[] = [];
    await page.route('**/api/v1/assistant/rooms/1/**', async (route) => {
      const url = route.request().url();
      let data: any = { messages: [] };
      if (url.endsWith('/chat'))
        data = {
          messages: [{ role: 'assistant', text: '正在前往 A-01，到达后查询告警。' }],
          clientAction: {
            token: 'walk-1',
            type: 'visit',
            cabinetSceneModelKey: 'A-01',
          },
        };
      if (url.endsWith('/scene-result')) {
        const receipt = route.request().postDataJSON();
        receipts.push(receipt);
        const reply = receipt.arrived
          ? '已到达 A-01 机柜。共有 1 条活动告警。'
          : '未确认到达 A-01，本次前往查询已停止。';
        data = { reply, messages: [{ role: 'assistant', text: reply }] };
      }
      await route.fulfill({ json: { code: 200, data } });
    });
    await page.goto('/home');
    await page.evaluate(async (outcome) => {
      const authPath = '/src/services/console.ts',
        servicePath = '/src/views/home/assistant/agent-assistant.ts';
      const patrolPath = '/src/views/home/scene/patrol.ts',
        threePath = '/src/views/home/scene/vendor/three.module.js';
      await (await import(authPath)).checkSession();
      const THREE = await import(threePath);
      const host = document.createElement('div');
      document.body.append(host);
      const patrol = (await import(patrolPath)).createPatrol({
        parent: new THREE.Group(),
        host,
        viewport: host,
        camera: new THREE.PerspectiveCamera(),
      });
      const device = { id: 'A-01', position: [-6, 0.1, -4.8] };
      const localQueries = 0;
      let reached = '';
      const scene = {
        getAgentState: () => ({ ready: true, selected: reached, devices: [{ id: 'A-01', doorOpen: false, doorAngle: 0, locked: false }], patrol: patrol.getState() }),
        visit: async () => { const result = await patrol.visit(device); if (!result.cancelled) reached = 'A-01'; return result; },
        cancelPatrol: () => patrol.cancel(),
        patrolState: () => patrol.getState(),
      };
      const assistant = (await import(servicePath)).createAgentAssistant(
        () => (outcome === 'unavailable' ? undefined : scene),
        {
          resolveCabinet: () => ({ state: 'LINKED' }),
        },
      );
      await assistant.enableAgent('1');
      (window as any).visitTest = { assistant, patrol, host, localQueries: () => localQueries };
      void assistant.command('去这个A-01机柜帮我查看告警信息');
    }, outcome);
    if (outcome !== 'unavailable') {
      await expect
        .poll(() => page.evaluate(() => (window as any).visitTest.patrol.getState().mode))
        .toBe('visiting');
      expect(receipts).toHaveLength(0);
      const positions = await page.evaluate(() => {
        const { patrol } = (window as any).visitTest;
        const before = patrol.getState().position;
        for (let i = 0; i < 10; i++) patrol.update(0.1, false, true);
        return { before, after: patrol.getState().position, mode: patrol.getState().mode };
      });
      expect(positions.after).not.toEqual(positions.before);
      expect(positions.mode).toBe('visiting');
      expect(receipts).toHaveLength(0);
      await page.evaluate(async (outcome) => {
        const { patrol, assistant } = (window as any).visitTest;
        if (outcome === 'cancelled') await assistant.scene.execute({ type: 'cancel' });
        else for (let i = 0; i < 500; i++) patrol.update(0.1, false, true);
      }, outcome);
    }
    await expect.poll(() => receipts.length).toBe(1);
    expect(receipts[0].arrived).toBe(outcome === 'arrived');
    if (outcome === 'arrived') expect(receipts[0].cabinetSceneModelKey).toBe('A-01');
    await expect
      .poll(() => page.evaluate(() => (window as any).visitTest.assistant.state.dialogueBusy))
      .toBe(false);
    expect(await page.evaluate(() => (window as any).visitTest.localQueries())).toBe(0);
    const text = await page.evaluate(
      () => (window as any).visitTest.assistant.state.messages.at(-1).text,
    );
    expect(text).toContain(outcome === 'arrived' ? '已到达 A-01' : '未确认到达 A-01');
    await page.evaluate(() => (window as any).visitTest.assistant.dispose());
  });
}
