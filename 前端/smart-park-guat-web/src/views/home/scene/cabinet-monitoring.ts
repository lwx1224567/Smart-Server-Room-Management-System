import type { SceneInput } from './scene.ts';
import type { SceneSnapshot } from '@/types/platform';
import type { useMonitoringStore } from '@/stores/monitoring';

export function syncCabinetMonitoring(
  scene: SceneInput,
  inventory: SceneSnapshot | undefined,
  monitoring: Pick<ReturnType<typeof useMonitoringStore>, 'cabinetAlarm' | 'cabinetStatus'>,
) {
  for (const cabinet of scene.devices) {
    const deviceIds = (inventory?.devices ?? [])
      .filter((device) => device.cabinetId === cabinet.databaseId)
      .map((device) => device.id);
    const alarm = monitoring.cabinetAlarm(cabinet.databaseId!, deviceIds);
    cabinet.alarm = alarm
      ? { title: alarm.title ?? '告警', severity: alarm.severity ?? 'warning' }
      : null;
    cabinet.runtimeStatus = monitoring.cabinetStatus(cabinet.databaseId!, deviceIds);
  }
}
