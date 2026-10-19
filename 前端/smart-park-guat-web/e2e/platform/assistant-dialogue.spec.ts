import { test, expect } from '@playwright/test';
import { agentFixture } from '../../tests/platform/agent-test-fixture';

test('server SSE deltas display before completion and final reply is merged once', async () => {
  let finish!: (value: any) => void;
  const f = agentFixture((route, _body, emit) => {
    if (route !== 'chat') return { messages: [] };
    emit({ type: 'status', data: { message: '正在查询告警' } }); emit({ type: 'message_start', data: {} });
    emit({ type: 'delta', data: { text: '共有' } });
    return new Promise(resolve => { finish = resolve; });
  });
  await f.conversation.enable('1'); const turn = f.conversation.command('查看告警');
  expect(f.state.dialogueBusy).toBe(true); expect(f.state.messages.at(-1).text).toBe('共有'); expect(f.state.status).toBe('正在查询告警');
  finish({ messages: [{ role: 'assistant', text: '共有 8 条告警' }], reply: '共有 8 条告警' }); await turn;
  expect(f.state.messages).toEqual([{ role: 'assistant', text: '共有 8 条告警' }]); expect(f.state.dialogueBusy).toBe(false); f.conversation.dispose();
});

test('page disposal releases its session and a new conversation has a distinct page ID', async () => {
  const a = agentFixture(); await a.conversation.enable('1'); await a.conversation.command('临时会话'); const id = a.calls[0].page; a.conversation.dispose();
  expect(a.calls.find(c => c.route === 'session/end')!.page).toBe(id);
  const b = agentFixture(); await b.conversation.enable('1'); expect(b.calls[0].page).not.toBe(id); expect(b.state.messages).toEqual([]); b.conversation.dispose();
});

test('selection creation assignment and retest remain server decisions; only card actions write', async () => {
  for (const kind of ['create', 'assign', 'retest']) {
    const f = agentFixture(route => route === 'chat' ? { messages: [{ role: 'assistant', text: '候选由服务端提供，请核对' }], pending: { kind, token: kind, summary: kind } } : { messages: [], confirmation: { status: 'SUCCEEDED' } });
    await f.conversation.enable('1');
    for (const text of ['选择第二个', kind, '确认', '好的']) await f.conversation.command(text);
    expect(f.calls.filter(c => c.route === 'chat').map(c => c.body.text)).toEqual(['选择第二个', kind, '确认', '好的']);
    expect(f.calls.some(c => c.route === 'confirm')).toBe(false);
    await f.conversation.confirm(); expect(f.calls.filter(c => c.route === 'confirm')).toHaveLength(1); expect(f.calls.at(-1)!.body.kind).toBe(kind); f.conversation.dispose();
  }
});
