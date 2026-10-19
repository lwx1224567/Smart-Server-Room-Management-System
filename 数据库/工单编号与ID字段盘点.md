# 工单公开编号与数据库 ID 盘点

盘点日期：2026-10-19。范围为当前开发库 `platform_smart_park` 的 ID、业务编号、关联字段，以及 JSON payload 内的 ID 字段。仅展示标识示例，不导出账号资料、密码、登录令牌或令牌哈希。

后续更新：此前列出的日志、请求、批次、历史备份和会话均已增加有规律的公开编号，见 [统一可读编号说明](统一可读编号说明.md)。下文保留最初盘点的原内部字段及历史建议，用于说明各字段关联；不代表仍缺少展示编号。

## 本次已完成：工单公开编号

格式为 `GD` + 创建日期（上海时区）+ 当日流水号，流水号至少四位，同一天跨机房统一分配。历史无有效创建时间时以补号日期为准。数据库唯一约束和日流水行锁确保并发分配不重号；超过四位自动扩位。

新增 `sp_order_number(order_id, order_code)` 和 `sp_order_day_sequence(order_day, sequence_value)`。内部 `console_order.id` 保持原值，接口新增 `orderCode`；页面、搜索和小维对话使用公开编号，工具参数可使用公开编号并由服务端解析回内部 ID。已有通知、工单告警关联和维修记录无需改号。

当前 7 张工单映射如下，按原始创建日期补号，并非全部采用本次处理日期：

| 原内部 ID（保留） | 新公开编号 |
| --- | --- |
| WO-778A66D9-453 | GD202610100001 |
| WO-EAE5A750-4F4 | GD202610130001 |
| WO-777120F7-191 | GD202610130002 |
| WO-02494C98-E85 | GD202610130003 |
| WO-CA3F990C-F81 | GD202610150001 |
| WO-2CD26BC2-377 | GD202610170001 |
| WO-C0B722D1-35E | GD202610190001 |

已实库验证：7 张工单均有唯一公开编号，重复补号映射不变；原始工单整行数据和通知 `order_id` 关联未发生改变。新建工单自动分配公开编号；服务重启会补齐仍未编号的历史工单。

## 仍为随机值或混合格式的字段

以下仅盘点，除本次工单公开编号外未改动这些 ID。

| 表及字段 | 实际格式 / 示例 | 用途与建议 |
| --- | --- | --- |
| `console_order.id`、`console_order.payload.id` | `WO-C0B722D1-35E`；共 7 张 | 工单内部主键，现已另设 GD 展示编号；保留原值 |
| `console_notice.order_id`、`sp_order_number.order_id` | 同一批 `WO-...` | 关联工单的外键式引用，不是新的编号体系；与内部主键一起保留 |
| `sp_monitor_record.id`，`kind=ALARM`；其 `payload.id` | `167d670a-0425-477b-aaa7-79339011b9e7`；共 44 条告警 | 内部告警 UUID，已另设 GJ 展示编号；保留原值 |
| `sp_alarm_number.alarm_id`、`console_order.payload.alarmEvent`、`payload.alarmSnapshot.id`，以及复检证据中的 `alarmEvent` | 同一批告警 UUID | 告警关联及历史证据，不宜单独改号；用户界面应显示 GJ 编号 |
| `sp_monitor_record.id` / `payload.id`，`kind=LOG` | UUID，例如 `0ae3b0f2-1921-4b2e-94a2-b331a0904840`，与历史种子格式混用；该类共 71 条日志 | **适合在确有展示需求时另设 `RZ + 日期 + 流水号`**；本次未修改 |
| `sp_monitor_record.payload.requestId`、`payload.response.requestId`，`kind=LOG` | `12a1006a-3eb0-4eff-843b-848ee3923ed4`；13 个不同请求标识 | 同一次请求的追踪、幂等及响应关联；建议保留，不用日期编号替换 |
| `sp_ingest_batch.batch_id` | `demo-temperature:12a1006a-3eb0-4eff-843b-848ee3923ed4`；共 13 条批次 | 前缀加请求 UUID，用于采集去重；建议保留。需要人工查批次时可另设展示编号 |
| `sp_source_backup.record_key`、`sp_source_backup.payload.id` | 含 `WO-...`、`1:ALARM:<UUID>` 和旧种子编号；共 199 条备份记录 | 迁移前原始快照及复合定位键，应保留，避免破坏历史审计依据 |
| `console_session.token_hash` | 安全哈希，未展示值 | 登录会话鉴权标识，随机性是有意设计，应保留并继续隐藏 |

同一 UUID 出现在多个关联字段中不代表重复生成或数据错误。JSON 内的告警快照、规则快照和复检证据也不应为了展示而重写原始标识。

## 有规律但命名风格不完全统一的字段

这些不是随机 ID；如要统一，应另行确认迁移范围和关联规则。

| 表 / 类型 | 示例 | 当前判断 |
| --- | --- | --- |
| `sp_device.code` | `CORE-01`、`AGG-01`、`GEN-A-03-1` | 设备类型不同采用不同前缀，具有规则，不必套用日期编号 |
| `sp_device.scene_model_key`、`legacy_key` | `core`、`agg`、`generated-A-03-1` | 三维及旧数据映射标识；保留，不能只改字符串 |
| `sp_monitor_record`，`EVENT` 的 ID | `seed-event-0` 等，共 12 条 | 有规则但含历史 seed 前缀；是否另设事件展示编号可另行决定 |
| `sp_monitor_record`，`FACILITY` 的 ID | `seed-ac-1` 等，共 15 条；场景键为 `ac-1` 等 | 与设施及三维场景映射有关，保留内部键 |
| `sp_monitor_record`，`NODE` / `LINK` 的 ID | `room-device-1` / `room-link-10`，分别 129 / 128 条 | 已有结构化编号，保留拓扑关系 |
| `sp_monitor_record`，`RULE` 的 ID 及各快照 `ruleId` | `demo-a01-temperature-v1` | 规则及版本标识，有规律；含历史 demo 前缀不等于随机编号 |
| `sp_park.code`、`sp_room.code`、`sp_cabinet.code`、`sp_zone.code` | `PARK-DEMO-01`、`IDC01`、`A-01`、`A` | 均有规则；前缀是否调整属于业务命名决策 |
| `sp_source_migration.id`、`sp_source_backup.migration_id` | `V008_UNIFIED_SOURCE` | 迁移版本标识，应保留 |
| 历史工单 JSON 中的 `ownerId` 与当前 `ownerAccountId` | 数值账号 ID | 是历史字段名兼容问题，不是 ID 随机问题；现有读取逻辑已兼容，未批量重写快照 |

## 正常的数字 ID

`console_account.id`、`console_notice.id`、旧 `user.id`，以及园区、机房、区域、机柜、设备、监测读数、能耗样本的 `id` 均为数字主键。对应的 `account_id`、`owner_account_id`、`park_id`、`room_id`、`zone_id`、`cabinet_id`、`device_id` 保持数字关联。`sp_reading.entity_id` 虽存为字符串，当前样本仍是数字实体 ID，需结合 `entity_type` 使用。

数字主键可能跳号，例如删除或回滚后不连续，这不属于无规律编号，也不应重新排号。账号资料及助手会话使用账号 / 机房组成关联或复合主键，同样无需改号。

## 验证与使用

- 前端类型检查通过；工单查询、复用、公开编号指派及原内部 ID 关联的相关后端测试通过。
- 重启后端并刷新浏览器后，新公开编号用于当前页面和后续回复；旧聊天文字不批量改写。
- `scripts/AuditDatabaseIds.java` 可重新生成只读盘点 JSON；`scripts/BackfillWorkOrderNumbers.java` 可补号并检查重复执行和关联保持情况。输出文件应放在已忽略的 `target/`。
