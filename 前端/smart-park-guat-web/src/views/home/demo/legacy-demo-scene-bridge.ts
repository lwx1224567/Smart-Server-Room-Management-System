import { nextTick } from 'vue';
type Scene = {
  visit(id: string): Promise<{ cancelled: boolean }>;
  cancelPatrol(): void;
  resumePatrol(): void;
  setAlarm(id: string, alarm: unknown): void;
  setDeviceDoor(id: string, open: boolean): void;
  patrolState(): unknown;
};
export function createLegacyDemoBridge(open: () => void, get: () => Scene | undefined) {
  let disposed = false;
  async function ensure() {
    if (disposed) return undefined;
    open();
    await nextTick();
    return get();
  }
  return {
    visit: async (id: string) => {
      const scene = await ensure();
      if (!scene) throw new Error('演示预览尚未就绪');
      return scene.visit(id);
    },
    cancelPatrol: () => get()?.cancelPatrol(),
    resumePatrol: () => {
      void ensure().then((scene) => scene?.resumePatrol());
    },
    setAlarm: (id: string, alarm: unknown) => {
      void ensure().then((scene) => scene?.setAlarm(id, alarm));
    },
    setDeviceDoor: (id: string, value: boolean) => get()?.setDeviceDoor(id, value),
    patrolState: () => get()?.patrolState(),
    dispose: () => {
      disposed = true;
      get()?.cancelPatrol();
    },
  };
}
