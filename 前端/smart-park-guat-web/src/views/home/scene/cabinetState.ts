import { reactive } from 'vue';
import { mockData } from '../demo/mockData.js';

export interface CabinetModelControls {
  lighting: boolean;
  fans: boolean;
  locked: boolean;
  power: boolean;
}

// 按机柜 ID 保存演示状态，页面或场景重新挂载后仍读取同一份状态。
export const cabinetControls = reactive<Record<string, CabinetModelControls>>(
  Object.fromEntries(
    mockData.devices.map((cabinet) => [
      cabinet.id,
      {
        lighting: true,
        fans: true,
        locked: false,
        power: true,
      },
    ]),
  ),
);
export const controlKeys: Record<string, keyof CabinetModelControls> = {
  机柜照明: 'lighting',
  散热风扇: 'fans',
  柜门门禁: 'locked',
  设备电源: 'power',
};

export interface DeviceModelAlarm {
  title: string;
  severity: 'critical' | 'warning' | 'normal' | 'info';
  demo: boolean;
}
// 独立的设备故障演示，与 A-02 的机柜进风温度告警无因果绑定。
export const deviceModelAlarms = reactive<Record<string, DeviceModelAlarm>>({
  srv3: { title: '设备散热风扇故障（演示）', severity: 'critical', demo: true },
});
