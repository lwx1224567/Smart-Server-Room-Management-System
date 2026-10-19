import { test, expect } from '@playwright/test';
import { agentFixture } from '../../tests/platform/agent-test-fixture';

test('every identity question reaches server Agent and prior success cannot mask later model failure', async () => {
  let fail = false;
  const f = agentFixture(route => {
    if (route !== 'chat') return { messages: [] };
    if (fail) throw new Error('model offline');
    return { messages: [{ role: 'assistant', text: '模型信息由服务端提供' }] };
  });
  await f.conversation.enable('1'); await f.conversation.command('你是什么模型'); fail = true; await f.conversation.command('你是什么模型');
  expect(f.calls.filter(c => c.route === 'chat')).toHaveLength(2); expect(f.state.status).toBe('Agent 请求失败');
  expect(f.state.messages.at(-1).text).toContain('model offline'); expect(f.calls.some(c => c.route === 'confirm')).toBe(false); f.conversation.dispose();
});
