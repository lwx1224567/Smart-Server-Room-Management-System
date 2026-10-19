import { createSceneActionExecutor, type AgentScene, type SceneAction } from './scene-action-executor';

/** Explicit UI actions only; no language parsing or business requests. */
export function createHomeSceneControls(getScene: () => AgentScene | undefined, resolve: (key: string) => { state: string }) {
  const executor = createSceneActionExecutor(getScene, resolve);
  function select(key: string) {
    const scene = getScene();
    if (!scene?.getAgentState().ready || resolve(key).state !== 'LINKED' || !scene.getAgentState().devices.some(d => d.id === key)) return false;
    scene.select(key);
    return scene.getAgentState().selected === key;
  }
  return { executor, select, execute(action: Omit<SceneAction, 'token'>) {
    return executor.execute({ ...action, token: crypto.randomUUID() });
  } };
}
