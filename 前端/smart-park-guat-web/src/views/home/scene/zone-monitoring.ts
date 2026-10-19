import type { SceneInput } from './scene.ts';

type ReadMetric = (
  entityType: string,
  entityId: string,
  metric: string,
) => { value: number } | undefined;

export function syncZoneMonitoring(scene: SceneInput, reading: ReadMetric) {
  for (const zone of scene.zones) {
    const cabinets = scene.devices.filter((device) => device.zone === zone.id);
    const average = (metric: string) => {
      const values = cabinets
        .map((cabinet) => reading('CABINET', cabinet.databaseId!, metric)?.value)
        .filter((value): value is number => value !== undefined);
      return values.length
        ? (values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1)
        : '未采集';
    };
    zone.temperature = average('temperature');
    zone.humidity = average('humidity');
  }
}
