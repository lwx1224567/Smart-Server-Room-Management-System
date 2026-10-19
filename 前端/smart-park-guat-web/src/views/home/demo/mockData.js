// 所有业务示例数据集中于此。图表和界面均由此对象生成；不请求后端。
export const mockData = {
  alarmOverview: {
    current: 18,
    today: 12,
    levels: [
      { name: '严重告警', count: 3, color: '#FF596B' },
      { name: '重要告警', count: 5, color: '#FFD263' },
      { name: '一般告警', count: 7, color: '#36A7FF' },
      { name: '提示告警', count: 3, color: '#31DFC1' },
    ],
    statuses: [
      { name: '未处理', count: 6, color: '#FF596B' },
      { name: '处理中', count: 8, color: '#36A7FF' },
      { name: '已恢复', count: 4, color: '#31DFC1' },
    ],
  },
  // 固定日期的演示周期；current 和 previous 使用相同统计口径。
  alarmTrend: {
    '24h': {
      label: '24小时',
      labels: Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`),
      current: [0, 1, 0, 0, 1, 0, 1, 2, 1, 2, 3, 1, 2, 3, 2, 1, 2, 1, 0, 1, 2, 1, 0, 1],
      previous: [1, 1, 0, 1, 1, 1, 2, 2, 1, 2, 3, 2, 2, 3, 2, 2, 2, 1, 1, 2, 2, 1, 1, 1],
    },
    '7d': {
      label: '7天',
      labels: ['10/01', '10/02', '10/03', '10/04', '10/05', '10/06', '10/07'],
      current: [18, 21, 16, 27, 24, 31, 22],
      previous: [22, 24, 20, 29, 27, 34, 25],
    },
    '30d': {
      label: '30天',
      labels: Array.from({ length: 30 }, (_, i) => {
        const date = new Date(Date.UTC(2026, 8, 8 + i));
        return `${String(date.getUTCMonth() + 1).padStart(2, '0')}/${String(date.getUTCDate()).padStart(2, '0')}`;
      }),
      current: [
        20, 18, 23, 25, 19, 21, 24, 22, 17, 26, 28, 23, 20, 18, 24, 27, 25, 21, 19, 23, 26, 24, 22,
        18, 21, 16, 27, 24, 31, 22,
      ],
      previous: [
        23, 21, 26, 28, 22, 24, 27, 25, 20, 29, 31, 26, 23, 21, 27, 30, 28, 24, 22, 26, 29, 27, 25,
        21, 24, 19, 30, 27, 34, 25,
      ],
    },
  },
  alarmSources: [
    { name: '设备', percent: 42 },
    { name: '环境', percent: 28 },
    { name: '电力', percent: 18 },
    { name: '网络', percent: 8 },
    { name: '安防', percent: 4 },
  ],
  // 本阶段仅展示，不建立场景映射或点击联动。
  frequentDevices: [
    { id: 'A001', name: 'A001机柜', count: 8, severity: 'critical' },
    { id: 'AC03', name: '空调03', count: 6, severity: 'warning' },
    { id: 'UPS01', name: 'UPS01', count: 5, severity: 'warning' },
    { id: 'SW-001', name: 'SW-001交换机', count: 3, severity: 'normal' },
    { id: 'A008', name: 'A008机柜', count: 2, severity: 'normal' },
  ],
  voiceCharacterMap: {
    來: '来',
    狀: '状',
    態: '态',
    開: '开',
    關: '关',
    閉: '闭',
    維: '维',
    檢: '检',
    確: '确',
    認: '认',
    單: '单',
    處: '处',
    復: '复',
    測: '测',
    設: '设',
    備: '备',
    區: '区',
    詢: '询',
    麼: '么',
    繼: '继',
    續: '续',
    護: '护',
    報: '报',
    為: '为',
    發: '发',
    現: '现',
    讀: '读',
    誤: '误',
    驟: '骤',
    進: '进',
    應: '应',
    運: '运',
    這: '这',
    個: '个',
    號: '号',
  },
  assistant: {
    name: '小维',
    voiceNotice: '当前使用演示数据，操作仅作用于本地页面。',
    wakeWords: ['小维', '小微', '小薇'],
    startPosition: [0, 0.1, 6.2],
    walkSpeed: 1.25,
    patrolRoute: [
      [-5.01, 0.1, 6.2],
      [-5.01, 0.1, -5.7],
      [4.59, 0.1, -5.7],
      [4.59, 0.1, 6.2],
      [0, 0.1, 6.2],
    ],
    maintenance: {
      last: '2024-10-28',
      next: '2024-11-28',
      owner: 'IDC 维保一组',
      normalAdvice: '检查进风口与滤网，确认双路供电、风扇和网络接口状态。',
    },
    welcome:
      '我是小维。输入“查看 A-02”，我会前往机柜并带你查看设备状态。也可以查看告警、开关柜门或返回总览。',
  },
  // 可视化配色：同一指标的图标、曲线与面板标题共用强调色。
  palette: {
    azure: '#3B9CFF',
    mint: '#31DFC1',
    violet: '#A88BFF',
    amber: '#FFC56D',
    coral: '#FF6D82',
    cyan: '#5CDCF3',
  },
  time: '2024-11-02 14:28:36',
  weather: { city: '上海', temperature: 18 },
  navigation: [
    { name: '总览', english: 'OVERVIEW', icon: 'overview', color: '#5CDCF3' },
    { name: '机房监控', english: 'MONITOR', icon: 'monitor', color: '#31DFC1' },
    { name: '资产管理', english: 'ASSETS', icon: 'server', color: '#A88BFF' },
    { name: '能效管理', english: 'ENERGY', icon: 'energy', color: '#FFC56D' },
    { name: '告警中心', english: 'ALARMS', icon: 'bell', color: '#FF6D82' },
    { name: '维保工单', english: 'WORK ORDERS', icon: 'work', color: '#81ABFF' },
  ],
  operation: {
    score: 98,
    description: '运行良好',
    devices: [
      { name: '设备总数', value: 256, color: '#168EFF' },
      { name: '在线设备', value: 252, color: '#39DBDF' },
      { name: '告警设备', value: 4, color: '#FF596B' },
      { name: '维护中', value: 0, color: '#FFCF67' },
    ],
  },
  environment: [
    {
      name: '温度',
      value: '24.3',
      unit: '℃',
      icon: 'temperature',
      color: '#FFC56D',
      trend: [23.8, 24.0, 23.9, 24.2, 24.1, 24.3, 24.2, 24.4, 24.3, 24.3],
    },
    {
      name: '湿度',
      value: '45.2',
      unit: '%',
      icon: 'humidity',
      color: '#5CDCF3',
      trend: [44.6, 44.9, 45.4, 45.1, 45.5, 45.2, 45.3, 44.9, 45.1, 45.2],
    },
    {
      name: 'PUE',
      value: '1.36',
      unit: '',
      icon: 'energy',
      color: '#31DFC1',
      trend: [1.39, 1.38, 1.4, 1.37, 1.38, 1.36, 1.37, 1.35, 1.36, 1.36],
    },
  ],
  alarms: {
    stats: [
      { name: '告警总数', value: 12, color: '#FFAC5E' },
      { name: '严重告警', value: 2, color: '#FF596B' },
      { name: '重要告警', value: 4, color: '#FFD263' },
      { name: '一般告警', value: 3, color: '#36A7FF' },
      { name: '提示告警', value: 3, color: '#31DFC1' },
    ],
    hourly: [0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 2, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0],
    criticalHours: [14],
    warningHours: [5, 10, 17, 22],
    alert: { time: '2024-11-02 13:24', message: 'A区温度超过阈值（28℃）' },
  },
  load: {
    percent: 68,
    current: '12.6',
    capacity: '18.5',
    unit: 'MW',
    trend: [
      8.2, 7.9, 7.6, 7.7, 7.6, 8.4, 8.5, 9.0, 10.7, 11.8, 11.6, 12.7, 11.9, 11.7, 12.4, 12.2, 11.4,
      11.8, 12.5, 12.0, 10.8, 10.7, 9.2, 8.6, 8.4,
    ],
  },
  energy: {
    today: 28430,
    unit: 'kWh',
    change: -6.8,
    hourly: [
      810, 760, 700, 670, 720, 780, 910, 1000, 1260, 1350, 1340, 1410, 1460, 1460, 1460, 1430, 1300,
      1200, 1240, 1380, 1430, 1270, 1190, 1020, 880,
    ],
  },
  distribution: [
    { name: '服务器', value: 58, color: '#198EFF' },
    { name: '存储设备', value: 18, color: '#31DFC1' },
    { name: '网络设备', value: 12, color: '#A88BFF' },
    { name: '安全设备', value: 7, color: '#FFC56D' },
    { name: '其他', value: 5, color: '#FF8092' },
  ],
  metrics: [
    { value: '256', unit: '', name: '机柜总数', icon: 'server' },
    { value: '32.6', unit: 'kW', name: '总功率', icon: 'power' },
    { value: '1.36', unit: '', name: 'PUE', icon: 'energy' },
    { value: '99.99', unit: '%', name: '可用性', icon: 'shield' },
  ],
  zones: [
    { id: 'A', temperature: '24.1', humidity: '44.8', position: [-5.7, 3.7, -4.3] },
    { id: 'B', temperature: '23.8', humidity: '45.0', position: [0, 4.1, -4.3] },
    { id: 'C', temperature: '24.5', humidity: '45.7', position: [5.7, 3.7, -4.3] },
    { id: 'D', temperature: '24.0', humidity: '45.1', position: [-4.9, 3.9, 4.8] },
    { id: 'E', temperature: '23.7', humidity: '45.4', position: [4.9, 3.9, 4.8] },
  ],
  // 程序化场景展示42个代表机柜；业务总数256为独立示例指标。
  room: { columns: 6, rows: 7, width: 18, depth: 14, rackHeight: 3.0 },
  deviceDefaults: {
    type: '42U 服务器机柜',
    capacity: 42,
    usedU: 36,
    serverCount: 12,
    voltage: 220,
    network: '双路 10 GbE',
    normalTemperature: 24.1,
  },
  activeAlarms: {
    'A-02': {
      severity: 'critical',
      title: '进风温度过高',
      metric: '温度',
      value: '31.8 ℃',
      threshold: '≤ 28 ℃',
      temperature: 31.8,
      time: '14:21:08',
      advice: '检查进风通道与冷通道送风，确认温度传感器读数。',
    },
    'C-05': {
      severity: 'critical',
      title: '散热风扇故障',
      metric: '风扇转速',
      value: '0 RPM',
      threshold: '≥ 1,200 RPM',
      temperature: 28.6,
      time: '14:18:32',
      advice: '检查第 5 层服务器风扇模组，确认备份散热工作状态。',
    },
    'D-07': {
      severity: 'warning',
      title: '网络丢包偏高',
      metric: '丢包率',
      value: '2.4 %',
      threshold: '≤ 1 %',
      temperature: 24.6,
      time: '14:12:45',
      advice: '检查上联端口、网线和交换机接口错误计数。',
    },
    'E-05': {
      severity: 'warning',
      title: 'PDU 负载偏高',
      metric: '负载率',
      value: '86 %',
      threshold: '≤ 80 %',
      temperature: 26.7,
      time: '14:09:16',
      advice: '检查双路供电分配，降低单路负载。',
    },
  },
  demoAlarm: {
    severity: 'critical',
    title: '进风温度过高',
    metric: '温度',
    value: '32.6 ℃',
    threshold: '≤ 28 ℃',
    temperature: 32.6,
    time: '14:28:36',
    advice: '演示告警：检查送风与进风通道。点击“恢复正常”可复位本台设备。',
  },
  hours: Array.from({ length: 25 }, (_, i) => `${String(i).padStart(2, '0')}:00`),
};

// 设备编号、空间分区、详情及告警共用同一份记录，避免模型和界面数量不一致。
const zoneCounts = {};
mockData.devices = Array.from({ length: mockData.room.columns }, (_, column) =>
  Array.from({ length: mockData.room.rows }, (_, row) => {
    const zone = row < 4 ? (column < 2 ? 'A' : column < 4 ? 'B' : 'C') : column < 3 ? 'D' : 'E';
    const number = (zoneCounts[zone] = (zoneCounts[zone] || 0) + 1);
    const id = `${zone}-${String(number).padStart(2, '0')}`;
    const alarm = mockData.activeAlarms[id] || null;
    return {
      id,
      zone,
      column,
      row,
      ...mockData.deviceDefaults,
      name: `${zone}区 ${String(number).padStart(2, '0')}号机柜`,
      serial: `IDC01-${zone}-${String(number).padStart(3, '0')}`,
      position: [-6 + column * 2.4, 0, -4.8 + row * 1.45],
      power: Number((2.1 + ((column * 7 + row) % 12) * 0.13).toFixed(2)),
      load: 48 + ((column * 7 + row) % 35),
      temperature:
        alarm?.temperature ?? Number((23.5 + ((column * 7 + row) % 13) * 0.1).toFixed(1)),
      normalTemperature: Number((23.5 + ((column * 7 + row) % 13) * 0.1).toFixed(1)),
      alarm,
      doorOpen: false,
    };
  }),
).flat();
mockData.zones.forEach((zone) => {
  zone.racks = zoneCounts[zone.id];
});

// 业务演示页的数据与初始工单，运行中改动只保存在内存。
mockData.business = {
  energyWeek: {
    labels: ['10/27', '10/28', '10/29', '10/30', '10/31', '11/01', '11/02'],
    kwh: [30120, 29750, 31260, 29980, 30740, 30505, 28430],
    pue: [1.42, 1.4, 1.41, 1.38, 1.39, 1.38, 1.36],
  },
  energyShares: [
    { name: 'IT 设备', value: 69, color: '#50b7ff' },
    { name: '制冷系统', value: 23, color: '#4de0bd' },
    { name: '供配电', value: 6, color: '#bd9aff' },
    { name: '照明与其他', value: 2, color: '#ffd17c' },
  ],
  energyTargets: { pue: 1.4, budget: 32000 },
  workOrders: [
    {
      id: 'WO-1102-001',
      device: 'A-02',
      title: '检查 A 区送风与温度传感器',
      priority: '紧急',
      status: '待处理',
      owner: '张工',
      created: '2024-11-02 14:23',
      note: '核对温度传感器读数，检查冷通道送风。',
      steps: ['告警生成工单'],
    },
    {
      id: 'WO-1102-002',
      device: 'C-05',
      title: '更换故障风扇模组',
      priority: '紧急',
      status: '进行中',
      owner: '李工',
      created: '2024-11-02 14:20',
      note: '确认备份散热正常后更换第 5 层风扇。',
      steps: ['告警生成工单', '维保人员已接单'],
    },
    {
      id: 'WO-1102-003',
      device: 'D-07',
      title: '检查交换机上联丢包',
      priority: '一般',
      status: '待处理',
      owner: '王工',
      created: '2024-11-02 14:15',
      note: '核对端口计数与网线连接。',
      steps: ['人工创建工单'],
    },
    {
      id: 'WO-1101-004',
      device: 'B-04',
      title: '机柜滤网例行清洁',
      priority: '计划',
      status: '已完成',
      owner: '张工',
      created: '2024-11-01 10:00',
      note: '滤网清洁与双路供电检查已完成。',
      steps: ['计划巡检', '维保人员已接单', '检查完成'],
    },
  ],
  guide: { scope: '全部业务记录均为示例，刷新页面恢复初始状态。' },
};
