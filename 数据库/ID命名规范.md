# ID 命名规范与本次调整

## 统一审计编号（2026-10-19）

日志新增 `logCode`（RZ）、请求新增 `requestCode`（QQ）、采集批次新增 `batchCode`（PC）、备份新增 `backup_code`（BF）、会话新增 `sessionCode`（HY），统一采用前缀 + 日期 + 当日流水号。原鉴权哈希、去重键和备份原文保留。V012 及应用启动逻辑负责历史补号，详见 `统一可读编号说明.md`。

## 工单公开编号（2026-10-19）

工单新增 `orderCode`：GD + 上海时区的创建日期 + 当日流水号（至少四位）。原 `console_order.id`、通知 `order_id` 和内部确认请求保持原值。V011 建立 `sp_order_number` 持久映射及日流水表；历史工单补号、新工单自动分配，页面与小维展示公开编号，内部操作仍以原 ID 关联。完整实库盘点及编号映射见 `工单编号与ID字段盘点.md`。

## 告警公开编号（2026-10-19）

告警内部 `id` 继续使用 UUID，工单 `alarmEvent` 继续关联该 UUID。新增 `alarmCode` 用于页面展示、搜索和小维对话，例如 `GJ202610190001`：`GJ` + 上海时区的创建日期 + 当日流水号（至少四位，超出后自动扩位）。同一天跨机房统一分配流水号，不按分钟生成，因此同一分钟的多条告警不会重号。

V010 创建 `sp_alarm_number` 持久映射和 `sp_alarm_day_sequence` 日流水表。服务启动时为历史告警按创建时间补号，之后创建告警自动分配；重复读取、更新和重启不改变编号，也不修改已有 UUID 或工单关联。历史缺少有效创建时间的告警以补号日期为准。小维查询告警使用服务端统一列表，详情序号与用户选择序号一致，可直接输入 GJ 编号选择告警。

数据库使用 snake_case，Java、TypeScript 和 JSON 使用 camelCase。

| 含义 | 数据库 | 代码／接口 |
| --- | --- | --- |
| 当前记录主键 | id | id |
| 园区、机房、区域关联 | park_id / room_id / zone_id | parkId / roomId / zoneId |
| 机柜、设备关联 | cabinet_id / device_id | cabinetId / deviceId |
| 登录账号关联 | account_id | accountId |
| 工单负责人账号 | owner_account_id | ownerAccountId |
| 业务编号 | code | code |
| 三维模型标识 | scene_model_key | sceneModelKey |
| 助手机柜场景动作目标 | 不作为数据库关联存储 | cabinetSceneModelKey |

`entity_type + entity_id` 保留，作为监测数据的多种实体引用；`entityId` 不能脱离 `entityType` 判断对象。复合主键及以关联字段作主键的资料表无需额外添加 id。UUID、工单编号与自增主键各自保留原有格式。2026-10-15 告警专项调整将旧种子告警 ID 统一为 UUID，同事务同步工单关联；其他记录不重新编号。

## 本次升级

- 工单列 `owner_id` 改为 `owner_account_id`，前后端、AI 工具参数及测试同步使用 `ownerAccountId`。
- 后端启动时检测旧列并改名；新数据库直接创建新列。V009 提供同等幂等 SQL，供手动维护使用。V003 保留历史建表定义，升级由 V009 或服务启动完成。
- 历史工单 payload 中的 `ownerId` 在读取时转换为 `ownerAccountId`；下次更新工单时写入新字段。未操作的历史 JSON 原文保留，查询原始 JSON 时可能仍看到旧名称。若两字段同时存在，以新字段为准。
- 助手发送给浏览器的场景动作与本地语音解析使用 `cabinetSceneModelKey`；维修历史查询中的 `deviceId` 仍为真实设备主键。前后端需一起更新并重启，浏览器刷新后使用新字段。
- 旧 `user.id` 与 `console_account.id` 属于不同编号空间。现有账号导入按用户名识别，不假定两者 ID 相同；当前工单负责人和会话关联均指向 `console_account.id`。
- 2025 年导出中的 `station_id` 仅作为历史记录保留，当前园区字段使用 `park_id`。

本次不修改现有 ID 值，不合并账号表，不重导种子。代码验证不等于已完成实际开发库升级；实际列改名在新后端首次启动或执行 V009 时发生。
