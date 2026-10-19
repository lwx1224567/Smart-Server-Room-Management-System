import operations from '@/assets/business-menu/operations.png';
import security from '@/assets/business-menu/security.png';
import visitor from '@/assets/business-menu/visitor.png';
import parking from '@/assets/business-menu/parking.png';
import park from '@/assets/business-menu/park.png';

export interface BusinessMenuItem {
  key: string;
  name: string;
  icon: string;
}

export const defaultBusinessMenuItems: BusinessMenuItem[] = [
  { key: 'operations', name: '机房运维', icon: operations },
  { key: 'energy', name: '能效管理', icon: security },
  { key: 'alarm', name: '告警中心', icon: visitor },
  { key: 'assets', name: '资产总览', icon: parking },
  { key: 'topology', name: '网络拓扑', icon: park },
];
