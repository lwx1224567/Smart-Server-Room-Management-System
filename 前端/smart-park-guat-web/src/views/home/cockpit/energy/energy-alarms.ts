import type { MonitorRecord } from '@/stores/monitoring';

// entityType identifies the affected asset; metric identifies an electrical alarm.
export function isElectricalAlarm(alarm: Pick<MonitorRecord, 'entityType' | 'metric'>): boolean {
  return (
    alarm.entityType === 'POWER' ||
    ['power', 'voltage', 'current', '功率', '电压', '电流'].includes(alarm.metric ?? '')
  );
}
