export const cardGroups: Record<string, { left: string[]; right: string[] }> = {
  home: {
    left: ['机房基础信息', '机房设施'],
    right: ['环境监控', '近 7 天能耗', '机房事件列表'],
  },
  operations: {
    left: ['设备定位', '设备状态', '柜内设备列表'],
    right: ['当前设备告警统计', '控制面板', '操作日志'],
  },
  energy: {
    left: ['实时能耗概览', '能耗分类统计', '用电异常告警'],
    right: ['PUE 指标', '机柜能耗 TOP5', '机房能耗趋势分析'],
  },
  alarm: {
    left: ['告警概览', '告警分析'],
    right: ['小维·巡检助手'],
  },
};
