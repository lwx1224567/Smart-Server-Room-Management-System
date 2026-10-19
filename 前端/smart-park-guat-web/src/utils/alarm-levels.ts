export const alarmLevels = [
  { key: 'critical', name: '严重告警', color: '#FF596B', rank: 0 },
  { key: 'warning', name: '重要告警', color: '#FFD263', rank: 1 },
  { key: 'normal', name: '一般告警', color: '#36A7FF', rank: 2 },
  { key: 'info', name: '提示告警', color: '#31DFC1', rank: 3 },
] as const;

export function alarmLevelName(severity?: string) {
  return alarmLevels.find((level) => level.key === severity)?.name ?? '未标明等级';
}

export function alarmLevelRank(severity?: string) {
  return alarmLevels.find((level) => level.key === severity)?.rank ?? 4;
}
