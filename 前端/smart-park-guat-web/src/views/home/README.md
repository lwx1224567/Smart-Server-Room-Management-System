# 首页代码放置指南

先根据业务找到目录，再区分页面布局、业务卡片和数据逻辑。业务组件不再用左上、右下等位置命名。

```text
home/
├─ index.vue                  首页入口：选择驾驶舱
├─ cockpit/                   六个业务驾驶舱
│  ├─ room/                   机房信息、设施、环境和事件
│  ├─ cabinet/                机柜详情、设备、控制和日志
│  ├─ energy/                 用电、分类能耗、PUE 和异常告警
│  ├─ alarms/                 告警概览、分析和助手入口
│  ├─ assets/                 资产类型、状态、位置、空间和视频
│  │  ├─ index.vue            页面布局
│  │  ├─ components/          资产卡片
│  │  ├─ composables/         资产统计、选择和详情请求
│  │  └─ styles/              资产页样式
│  └─ topology/               网络节点、链路、端口和拓扑下钻
├─ composables/
│  ├─ useHomeDashboard.ts     导航、助手和页面生命周期
│  ├─ useHomeSceneData.ts     台账和监测同步到三维场景
│  └─ useCabinetControls.ts   机柜控制请求、状态和操作日志
├─ components/                首页共享 UI：视频播放器、空状态
├─ scene/                     三维渲染、动画和数据适配
├─ assistant/                 Agent 会话、语音、确认卡片与结构化场景动作
├─ demo/                      仍需兼容的演示数据和流程桥接
└─ styles/                    驾驶舱共用布局
```

## 驾驶舱内部怎么找代码

- `index.vue`：卡片布局和事件连接。
- `components/*Card.vue`：业务卡片，例如 RoomInfoCard、ElectricalAlarmsCard、CabinetControlCard；调整位置只改布局，不改组件名。
- `components/index.ts`：集中导出本模块卡片。
- `composables/`：状态、请求与交互协调；纯转换函数放模块内，例如 energy-alarms.ts。
- `shared/`：本模块共用实现。OperationsCard.vue 和 EnergyManagementCard.vue 仍通过 kind 复用渲染，修改时检查所有分支。
- `home/components/`：首页共享 UI。例如视频播放器不再放在拓扑业务私有目录。

## 修改功能的入口

| 功能           | 文件                                                                              |
| -------------- | --------------------------------------------------------------------------------- |
| 机房信息       | cockpit/room/components/RoomInfoCard.vue                                          |
| 设施列表       | cockpit/room/components/FacilityListCard.vue                                      |
| 柜内设备       | cockpit/cabinet/components/DeviceListCard.vue                                     |
| 控制请求与日志 | composables/useCabinetControls.ts                                                 |
| 用电异常       | cockpit/energy/energy-alarms.ts、shared/EnergyManagementCard.vue                  |
| 告警分析       | cockpit/alarms/components/AlarmAnalysisCard.vue                                   |
| 资产数据与选择 | cockpit/assets/composables/useAssetDashboard.js                                   |
| 视频播放       | components/VideoSurveillance.vue                                                  |
| 拓扑下钻和搜索 | cockpit/topology/DrilldownTopology.vue、topology-drilldown.ts、topology-search.ts |
| 模型与动画     | scene/RoomScene.vue、scene.ts、patrol.ts                                          |
| 助手建单       | assistant/agent-assistant.ts、agent-conversation.ts（服务端准备与按钮确认）                                                         |

全局接口、共享状态和类型仍在 src/api、src/stores、src/types；跨页面工单在 src/services/work-orders.ts。demo 仍被正式模块引用，不能整体删除。

小维当前架构、API 和验证边界见 [小维 Agent 当前架构与接口](../../../../../docs/小维Agent当前架构与接口.md)。历史阶段报告仅用于审计。
