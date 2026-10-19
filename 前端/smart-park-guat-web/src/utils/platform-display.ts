import type { DataMeta } from '@/types/platform';

export function originLabel(meta?: Pick<DataMeta, 'recordOrigin'>): string {
  return meta?.recordOrigin === 'DEMO' || meta?.recordOrigin === 'REAL' ? '真实数据' : '来源未确认';
}
export function runtimeLabel(status?: string | null): string {
  if (!status || status === 'UNKNOWN') return '未采集';
  return ({ NORMAL: '正常', ABNORMAL: '异常' } as Record<string, string>)[status] ?? status;
}
export function readingLabel(value?: number | null): string {
  return value == null || !Number.isFinite(value) ? '--' : String(value);
}
export function uPositionLabel(start?: number | null, height?: number | null): string {
  return start == null || height == null ? '未登记' : `${start}U · ${height}U高`;
}
export function registrationLabel(status: string): string {
  return status === 'ACTIVE' ? '登记有效' : status === 'INACTIVE' ? '登记无效' : '登记状态未知';
}
