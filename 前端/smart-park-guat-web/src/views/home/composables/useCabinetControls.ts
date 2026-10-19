import { computed, reactive, watch, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { usePlatformStore } from '@/stores/platform';
import {
  useMonitoringStore,
  type CabinetControlRecord,
  type MonitorRecord,
} from '@/stores/monitoring';
import { saveCabinetControl } from '@/api/platform/client';
import { integrationApi } from '@/api/platform/integrations';
import type { CabinetOperations } from '../cockpit/cabinet/shared/OperationsCard.vue';
import type { SceneInput } from '../scene/scene.ts';
import type { Cabinet } from '@/types/platform';
import type RoomScene from '../scene/RoomScene.vue';
import { controlKeys } from '../scene/cabinetState';

interface CabinetControlContext {
  platform: ReturnType<typeof usePlatformStore>;
  monitoring: ReturnType<typeof useMonitoringStore>;
  formalSceneData: Ref<SceneInput | undefined>;
  currentCabinet: Readonly<Ref<Cabinet | undefined>>;
  roomScene: Ref<InstanceType<typeof RoomScene> | undefined>;
  hardwareControls: Ref<boolean>;
  refreshMonitoring: () => Promise<void>;
}

// Cabinet controls, pending requests and operation logs share one lifecycle.
export function useCabinetControls({
  platform,
  monitoring,
  formalSceneData,
  currentCabinet,
  roomScene,
  hardwareControls,
  refreshMonitoring,
}: CabinetControlContext) {
  const operationsByCabinet = reactive<Record<string, CabinetOperations>>({});
  watch(
    formalSceneData,
    (value) => {
      if (value)
        for (const id of Object.keys(value.controls))
          if (!operationsByCabinet[id])
            operationsByCabinet[id] = {
              controls: Object.keys(controlKeys).map((name) => ({
                name,
                on: value.controls[id]![controlKeys[name]!],
              })),
              logs: [],
            };
      if (value)
        for (const [id, operations] of Object.entries(operationsByCabinet))
          for (const control of operations.controls) {
            const key = controlKeys[control.name];
            if (key) control.on = value.controls[id]?.[key] ?? false;
          }
    },
    { deep: true },
  );
  const pendingControls = new Set<string>();
  async function updateControl(key: string, value: boolean) {
    const modelKey = currentCabinet.value?.sceneModelKey;
    if (!modelKey || !formalSceneData.value) return;
    if (key === 'locked' && value && roomScene.value?.isDoorOpen()) {
      ElMessage.warning('请先关闭柜门，再锁定门禁');
      return;
    }
    const state = formalSceneData.value.controls[modelKey];
    if (!state || !(key in state)) return;
    const cabinetId = currentCabinet.value!.id,
      roomId = platform.room!.id,
      pendingKey = `${cabinetId}:${key}`;
    if (pendingControls.has(pendingKey)) return;
    pendingControls.add(pendingKey);
    try {
      if (hardwareControls.value) {
        const command = await integrationApi.command(roomId, cabinetId, key, value);
        if (command.status !== 'SUCCEEDED')
          throw new Error(command.message || '设备执行失败，状态未更新');
        await refreshMonitoring();
        return;
      }
      const result = await saveCabinetControl<{
        controls: CabinetControlRecord;
        log: MonitorRecord;
      }>(roomId, cabinetId, key, value);
      Object.assign(state, result.controls);
      if (monitoring.snapshot?.roomId === roomId) {
        monitoring.snapshot.logs.unshift(result.log);
        const controls = monitoring.snapshot.controls ?? (monitoring.snapshot.controls = []);
        const existing = controls.find((c) => c.cabinetId === cabinetId);
        if (existing) Object.assign(existing, result.controls);
        else controls.push(result.controls);
      }
    } catch (e) {
      ElMessage.error((e as Error).message);
    } finally {
      pendingControls.delete(pendingKey);
    }
  }
  watch(
    () => [monitoring.snapshot?.controls, formalSceneData.value] as const,
    () => {
      for (const item of monitoring.snapshot?.controls ?? []) {
        const cabinet = platform.scene?.cabinets.find((c) => c.id === item.cabinetId);
        const state = cabinet && formalSceneData.value?.controls[cabinet.sceneModelKey];
        if (state) for (const key of Object.values(controlKeys)) state[key] = item[key];
      }
    },
    { deep: true },
  );

  const currentOperations = computed(() => {
    const cabinet = currentCabinet.value,
      operations = cabinet ? operationsByCabinet[cabinet.sceneModelKey] : undefined;
    if (!cabinet || !operations) return undefined;
    return {
      ...operations,
      logs: (monitoring.snapshot?.logs ?? [])
        .filter((log) => log.cabinetId === cabinet.id)
        .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
        .map((log) => ({
          action: log.action ?? '',
          logCode: log.logCode,
          user: log.user ?? '--',
          time: log.createdAt
            ? new Date(log.createdAt).toLocaleTimeString('zh-CN', { hour12: false })
            : '--',
        })),
    };
  });

  return { currentOperations, updateControl };
}
