interface RoomDevice {
  position: number[];
  column: number;
  row: number;
  runtimeStatus?: string;
  id: string;
  name: string;
  zone: string;
  doorOpen: boolean;
  alarm: {
    title: string;
    severity: string;
    metric: string;
    value: string | number;
    threshold: string | number;
    advice: string;
  } | null;
}
export const mockData: {
  room: { columns: number; rows: number; width: number; depth: number; rackHeight: number };
  assistant: { startPosition: number[]; walkSpeed: number; patrolRoute: number[][] };
  zones: import('../scene/types').SceneInput['zones'];

  alarmOverview: {
    current: number;
    today: number;
    levels: { name: string; count: number; color: string }[];
    statuses: { name: string; count: number; color: string }[];
  };
  alarmTrend: Record<
    '24h' | '7d' | '30d',
    { label: string; labels: string[]; current: number[]; previous: number[] }
  >;
  alarmSources: { name: string; percent: number }[];
  frequentDevices: {
    id: string;
    name: string;
    count: number;
    severity: 'critical' | 'warning' | 'normal' | 'info';
  }[];
  devices: RoomDevice[];
  activeAlarms: Record<string, { title: string; severity: string }>;
  alarms: { stats: { name: string; value: number; color: string }[] };
};
