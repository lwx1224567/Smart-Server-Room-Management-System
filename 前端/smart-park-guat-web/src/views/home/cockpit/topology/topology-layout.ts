import type { MonitorRecord } from '@/stores/monitoring';

export interface TopologyLink extends MonitorRecord {
  memberCount: number;
}
