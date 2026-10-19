import { cameras } from './cameras';

// 场景设施尚未登记到数据库，数量与定位均明确作为演示设施。
export const airConditioners = [-8, 8].flatMap((x, side) =>
  [-5.3, -2.9, -0.5, 1.9, 4.3].map((z, row) => ({
    id: `ac-${side * 5 + row + 1}`,
    name: `精密空调 ${String(side * 5 + row + 1).padStart(2, '0')}`,
    position: [x, 1.43, z] as [number, number, number],
  })),
);
export const demoFacilities = [
  ...cameras.map((item) => ({ ...item, type: 'camera' as const })),
  ...airConditioners.map((item) => ({ ...item, type: 'aircondition' as const })),
];
