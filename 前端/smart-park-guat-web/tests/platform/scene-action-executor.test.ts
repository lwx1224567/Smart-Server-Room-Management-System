import { test, expect } from '@playwright/test';
import { createSceneActionExecutor, type AgentScene, type SceneAction } from '../../src/views/home/assistant/scene-action-executor';

function fixture() {
  const device = { id: 'A-01', doorOpen: false, doorAngle: 0, locked: false };
  const state = { ready: true, selected: '', devices: [device], patrol: { mode: 'paused' } };
  let calls = 0;
  const scene: AgentScene = {
    getAgentState: () => state,
    select: id => { state.selected = id; },
    visit: async id => { state.selected = id; return { cancelled: false, deviceId: id }; },
    setDeviceDoor: (_, open) => { calls++; device.doorOpen = open; device.doorAngle = open ? -Math.PI * .61 : 0; },
    resumePatrol: () => { state.patrol.mode = 'patrol'; },
    cancelPatrol: () => { state.patrol.mode = 'paused'; },
  };
  const executor = createSceneActionExecutor(() => scene, key => ({ state: key === 'A-01' ? 'LINKED' : 'UNLINKED' }));
  return { device, state, scene, executor, calls: () => calls };
}
test('all five actions confirm actual state and duplicate token does not replay', async () => {
  const f = fixture();
  for (const type of ['visit', 'status', 'door', 'resume', 'cancel'] as const) {
    const action: SceneAction = { token: type, type, ...(['visit','status','door'].includes(type) ? { cabinetSceneModelKey: 'A-01' } : {}), ...(type === 'door' ? { open: true } : {}) };
    const receipt = await f.executor.execute(action);
    expect(receipt.status).toBe('SUCCEEDED');
    expect(await f.executor.execute(action)).toEqual(receipt);
  }
  expect(f.calls()).toBe(1);
  expect((await f.executor.execute({ token: 'door', type: 'door', cabinetSceneModelKey: 'A-01', open: false })).code).toBe('TOKEN_CONFLICT');
});
test('rejects invalid token, unknown actions, parameters and missing targets', async () => {
  const f = fixture();
  for (const action of [
    { token: '', type: 'resume' }, { token: 'unknown', type: 'sql' },
    { token: 'missing', type: 'visit' }, { token: 'foreign', type: 'status', cabinetSceneModelKey: 'B-01' },
    { token: 'bool', type: 'door', cabinetSceneModelKey: 'A-01', open: 'true' },
    { token: 'extra', type: 'resume', command: 'anything' },
  ]) expect((await f.executor.execute(action as SceneAction)).status).toBe('FAILED');
  expect(f.calls()).toBe(0);
});
test('not ready, locked door, execution exception and wrong arrival fail explicitly', async () => {
  const f = fixture();f.state.ready = false;
  expect((await f.executor.execute({ token:'ready',type:'resume' })).code).toBe('SCENE_NOT_READY');
  f.state.ready = true;f.device.locked = true;
  expect((await f.executor.execute({ token:'lock',type:'door',cabinetSceneModelKey:'A-01',open:true })).code).toBe('DOOR_LOCKED');
  f.scene.visit = async () => ({ cancelled:false,deviceId:'B-01' });
  expect((await f.executor.execute({ token:'wrong',type:'visit',cabinetSceneModelKey:'A-01' })).code).toBe('TARGET_MISMATCH');
  f.scene.select = () => { throw new Error('scene failure'); };
  expect((await f.executor.execute({ token:'error',type:'status',cabinetSceneModelKey:'A-01' })).code).toBe('EXECUTION_FAILED');
});
test('door receipt waits for angle and cancellation never claims completion', async () => {
  const f = fixture();f.scene.setDeviceDoor = (_, open) => { f.device.doorOpen = open; };
  let done = false;
  const first = f.executor.execute({ token:'animated',type:'door',cabinetSceneModelKey:'A-01',open:true }).then(r => {done=true;return r;});
  await new Promise(r=>setTimeout(r,60));expect(done).toBe(false);
  f.device.doorAngle=-Math.PI*.61;expect((await first).status).toBe('SUCCEEDED');
  const second=f.executor.execute({token:'cancel-door',type:'door',cabinetSceneModelKey:'A-01',open:false});
  f.executor.cancel();expect((await second).status).toBe('CANCELLED');
});
test('cancelled patrol returns cancelled receipt', async () => {
  const f=fixture();f.scene.visit=async()=>({cancelled:true});
  expect((await f.executor.execute({token:'cancel-walk',type:'visit',cabinetSceneModelKey:'A-01'})).status).toBe('CANCELLED');
});
test('unfinished animation times out without success', async () => {
  const f=fixture();f.scene.setDeviceDoor=(_,open)=>{f.device.doorOpen=open;};
  const receipt=await f.executor.execute({token:'timeout',type:'door',cabinetSceneModelKey:'A-01',open:true});
  expect(receipt.status).toBe('FAILED');expect(receipt.code).toBe('ANIMATION_TIMEOUT');
});
