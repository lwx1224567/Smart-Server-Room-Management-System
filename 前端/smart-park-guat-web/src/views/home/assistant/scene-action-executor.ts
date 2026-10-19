export type SceneActionType = 'visit' | 'status' | 'door' | 'resume' | 'cancel';
export interface SceneAction {
  token: string;
  type: SceneActionType;
  cabinetSceneModelKey?: string;
  open?: boolean;
  queryAfterArrival?: 'alarms';
}
export interface SceneReceipt {
  token: string;
  type: SceneActionType;
  status: 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
  cabinetSceneModelKey?: string;
  open?: boolean;
  arrived: boolean;
  code?: string;
  result: string;
}
export interface AgentScene {
  getAgentState(): {
    ready: boolean;
    selected: string;
    devices: { id: string; doorOpen: boolean; doorAngle: number; locked: boolean }[];
    patrol?: { mode: string; target?: string };
  };
  visit(id: string): Promise<{ cancelled: boolean; deviceId?: string }>;
  select(id: string): void;
  setDeviceDoor(id: string, open: boolean): void;
  resumePatrol(): void;
  cancelPatrol(): void;
}

/** Structured scene commands only. No language parsing or business API access. */
export function createSceneActionExecutor(
  getScene: () => AgentScene | undefined,
  resolveCabinet: (key: string) => { state: string },
) {
  let generation = 0;
  const receipts = new Map<string, { fingerprint: string; promise: Promise<SceneReceipt> }>();
  const cancel = () => { generation++; getScene()?.cancelPatrol(); };
  function execute(action: SceneAction): Promise<SceneReceipt> {
    const reply = (status: SceneReceipt['status'], code: string, result: string): SceneReceipt => ({
      token: action?.token || '', type: action?.type, status, code, result,
      cabinetSceneModelKey: action?.cabinetSceneModelKey, open: action?.open,
      arrived: status === 'SUCCEEDED' && action.type === 'visit',
    });
    const fail = (code: string, result: string) => Promise.resolve(reply('FAILED', code, result));
    if (!action || typeof action.token !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(action.token)) return fail('INVALID_TOKEN', '场景令牌无效');
    if (!['visit', 'status', 'door', 'resume', 'cancel'].includes(action.type)) return fail('UNKNOWN_ACTION', '未知场景动作');
    if (Object.keys(action).some(k => !['token', 'type', 'cabinetSceneModelKey', 'open', 'queryAfterArrival'].includes(k))) return fail('INVALID_ARGUMENT', '存在未知场景参数');
    const targeted = ['visit', 'status', 'door'].includes(action.type);
    if (targeted ? typeof action.cabinetSceneModelKey !== 'string' || !action.cabinetSceneModelKey.trim() : !!action.cabinetSceneModelKey) return fail('INVALID_TARGET', '场景目标参数无效');
    if (action.type === 'door' ? typeof action.open !== 'boolean' : action.open !== undefined) return fail('INVALID_ARGUMENT', '柜门参数必须为布尔值且仅用于柜门动作');
    if (action.queryAfterArrival !== undefined && (action.type !== 'visit' || action.queryAfterArrival !== 'alarms')) return fail('INVALID_ARGUMENT', '到达后操作参数无效');
    const fingerprint = JSON.stringify([action.type, action.cabinetSceneModelKey, action.open, action.queryAfterArrival]);
    const previous = receipts.get(action.token);
    if (previous) return previous.fingerprint === fingerprint ? previous.promise : fail('TOKEN_CONFLICT', '同一令牌的动作内容不一致');
    const promise = (async () => {
      try {
        const scene = getScene();
        if (!scene?.getAgentState().ready) return reply('FAILED', 'SCENE_NOT_READY', '三维场景尚未就绪');
        const key = action.cabinetSceneModelKey || '';
        const device = () => scene.getAgentState().devices.find(d => d.id === key);
        if (targeted && (resolveCabinet(key).state !== 'LINKED' || !device())) return reply('FAILED', 'TARGET_NOT_FOUND', '目标机柜不存在或未关联当前台账');
        if (action.type === 'cancel') {
          cancel();
          return scene.getAgentState().patrol?.mode === 'paused' ? reply('SUCCEEDED', 'CANCELLED_PATROL', '已取消三维巡检任务') : reply('FAILED', 'STATE_MISMATCH', '未确认巡检已停止');
        }
        const mine = ++generation;
        if (action.type === 'resume') {
          scene.resumePatrol();
          return scene.getAgentState().patrol?.mode === 'patrol' ? reply('SUCCEEDED', 'PATROL_RESUMED', '三维巡逻已恢复') : reply('FAILED', 'STATE_MISMATCH', '未确认巡逻已恢复');
        }
        if (action.type === 'status') {
          scene.select(key);
          return scene.getAgentState().selected === key ? reply('SUCCEEDED', 'LOCATED', `已定位三维机柜 ${key}`) : reply('FAILED', 'STATE_MISMATCH', '未确认定位目标');
        }
        if (action.type === 'visit') {
          const result = await scene.visit(key);
          if (mine !== generation || result.cancelled) return reply('CANCELLED', 'ACTION_CANCELLED', '三维巡检已取消');
          if (getScene() !== scene || !scene.getAgentState().ready) return reply('FAILED', 'SCENE_NOT_READY', '场景已卸载');
          if (resolveCabinet(key).state !== 'LINKED' || !device()) return reply('FAILED', 'TARGET_NOT_FOUND', '目标机柜关联已变化');
          return result.deviceId === key && scene.getAgentState().selected === key ? reply('SUCCEEDED', 'ARRIVED', `已到达三维机柜 ${key}`) : reply('FAILED', 'TARGET_MISMATCH', '巡检到达目标不一致');
        }
        if (device()?.locked) return reply('FAILED', 'DOOR_LOCKED', '三维柜门已锁定');
        scene.setDeviceDoor(key, action.open!);
        const started = Date.now(), desired = action.open ? -Math.PI * 0.61 : 0;
        while (Date.now() - started < 8000) {
          if (mine !== generation) return reply('CANCELLED', 'ACTION_CANCELLED', '柜门动画等待已取消，未撤销已应用的模型状态');
          if (getScene() !== scene || !scene.getAgentState().ready) return reply('FAILED', 'SCENE_NOT_READY', '场景已卸载');
          const current = device();
          if (!current || resolveCabinet(key).state !== 'LINKED') return reply('FAILED', 'TARGET_NOT_FOUND', '目标机柜已移除或关联已变化');
          if (current.locked) return reply('FAILED', 'DOOR_LOCKED', '三维柜门已锁定');
          if (current.doorOpen !== action.open) return reply('FAILED', 'STATE_MISMATCH', '柜门目标状态已变化');
          if (Math.abs(current.doorAngle - desired) < 0.001) return reply('SUCCEEDED', 'DOOR_SETTLED', `三维机柜 ${key} 柜门${action.open ? '打开' : '关闭'}动画已完成`);
          await new Promise(resolve => setTimeout(resolve, 25));
        }
        return reply('FAILED', 'ANIMATION_TIMEOUT', '柜门动画尚未确认完成');
      } catch { return reply('FAILED', 'EXECUTION_FAILED', '三维场景动作执行失败'); }
    })();
    receipts.set(action.token, { fingerprint, promise });
    return promise;
  }
  return { execute, cancel };
}
