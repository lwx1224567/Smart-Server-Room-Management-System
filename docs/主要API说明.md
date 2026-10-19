# 主要 API 说明

接口前缀：`/api/v1`。以下根据当前 Controller 与请求 DTO 整理，覆盖主要业务，不是已生成的 OpenAPI 规范。

## 鉴权与返回结构

登录使用 `POST /auth/login`，JSON：

```json
{"userName":"demo_admin","password":"替换为自行设置的密码"}
```

成功响应的 `data.token` 是会话令牌，后续请求添加 `Authorization: Bearer <token>`。会话当前有效期为 8 小时，退出或修改密码会撤销相应会话。下文「登录」表示需有效会话，「管理员」表示另需 `ADMIN` 角色；维保身份为 `MAINTAINER`。

普通 JSON 响应使用以下结构；字段值仅为格式示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": {},
  "requestId": "请求标识",
  "serverTime": "2026-10-19T00:00:00Z",
  "error": null
}
```

请求体使用 `Content-Type: application/json`。同时检查 HTTP 状态和响应中的 `code`。常见错误：400 参数错误、401 未登录/会话失效、403 权限不足、404 记录不存在、409 状态冲突。冲突后重新读取数据，不要盲目重试写操作。

## 账号与人员

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 无需登录 | `userName`、`password` |
| GET | `/auth/me` | 登录 | 当前账号资料 |
| POST | `/auth/logout` | 携带已有令牌 | 撤销会话 |
| PUT | `/auth/password` | 登录 | `oldPassword`、`newPassword` |
| GET / POST | `/users` | 管理员 | 维保人员列表 / 新建维保人员 |
| PUT / DELETE | `/users/{id}` | 管理员 | 编辑 / 删除维保人员 |
| GET | `/notifications` | 登录 | 当前账号通知 |
| PUT | `/notifications/{id}/read` | 登录 | 标记自己的通知已读 |
| DELETE | `/notifications/read` | 登录 | 删除自己的已读通知 |
| GET | `/weather` | 登录 | 天气信息 |

## 台账与大屏

| 方法 | 路径 | 主要参数或用途 |
| --- | --- | --- |
| GET | `/rooms` | `q`、`recordOrigin`、`page`、`pageSize`、`sort` |
| GET | `/rooms/{roomId}` | 机房详情 |
| GET | `/rooms/{roomId}/scene` | 三维场景台账；可选 `recordOrigin` |
| GET | `/rooms/{roomId}/cabinets` | `zoneId`、`q`、`page`、`pageSize`、`sort` |
| GET | `/cabinets/{cabinetId}` | 机柜详情 |
| GET | `/cabinets/{cabinetId}/devices` | 设备列表，可按 `q`、`type` 筛选 |
| GET / POST | `/admin/{kind}` | 台账列表 / 新建 |
| GET / PUT / DELETE | `/admin/{kind}/{id}` | 台账详情 / 编辑 / 删除 |

以上均要求管理员。`kind` 为 `parks`、`rooms`、`zones`、`cabinets`、`devices`。台账字段按实体和关联约束填写；ID 从返回值读取，不依赖固定数字。管理员台账列表使用 `page`、`pageSize`，每页上限 100；普通台账列表的默认排序为 `id,asc`。

## 监测、能耗与集成

以下接口均要求管理员，`{room}` 与 `{roomId}` 均为机房 ID：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/rooms/{roomId}/monitoring` | readings、alarms、facilities、events、nodes、links、logs、controls 快照 |
| GET | `/rooms/{roomId}/readings` | 机房读数 |
| GET | `/rooms/{roomId}/alarms` | 告警列表 |
| GET | `/rooms/{roomId}/facilities` | 摄像头、空调等设施 |
| GET | `/rooms/{roomId}/network/topology` | nodes、links |
| GET | `/rooms/{roomId}/operation-logs` | 操作日志 |
| GET | `/rooms/{roomId}/energy` | `period=today`、`7d` 或 `30d` |
| PUT | `/rooms/{roomId}/alarms/{id}/acknowledge` | 确认告警 |
| PUT | `/rooms/{roomId}/cabinets/{id}/controls` | 本地控制状态：`key` 与布尔 `value` |
| GET | `/integrations` | 模型、视频、控制等配置能力状态 |
| GET | `/integrations/collector` | 采集状态 |
| POST | `/integrations/collector/pull` | 触发外部采集 |
| POST | `/rooms/{room}/ingest` | 提交监测批次；需符合服务端采集契约 |
| GET | `/rooms/{room}/readings/history` | `entityType`、`entityId`、`metric`、`from`、`to` |
| GET | `/rooms/{room}/alarm-rules` | 告警规则 |
| PUT / DELETE | `/rooms/{room}/alarm-rules/{id}` | 保存 / 删除规则 |
| POST | `/rooms/{room}/alarms/evaluate` | 评估规则，`recordOrigin` 默认 REAL |
| GET / PUT | `/rooms/{room}/cameras/{id}/video` | 获取 / 配置视频来源 |
| GET / POST | `/rooms/{room}/cabinets/{id}/commands` | 查询命令 / 向控制网关下发命令 |

`controls` 本地状态更新与 `commands` 外部控制命令是不同能力，接口成功不应被统一解释为物理设备已动作。视频配置、采集与控制网关未配置时，相应能力不会自动接入。

## 工单

| 方法 | 路径 | 权限及请求 |
| --- | --- | --- |
| GET | `/work-orders` | 登录；管理员查全局，维保按服务端权限查看 |
| GET | `/work-orders/history` | 登录；必填 `roomId`，可选 `cabinetId`、`deviceId`、`recordOrigin` |
| POST | `/work-orders` | 管理员；`title`、`device` 必填，建议指定 `roomId`，告警转单传 `alarmEvent` |
| POST | `/work-orders/{id}/assign` | 管理员；`ownerAccountId` 为启用的维保账号 ID |
| POST | `/work-orders/{id}/claim` | 维保领取待受理工单 |
| POST | `/work-orders/{id}/start` | 负责人开始处理 |
| PATCH | `/work-orders/{id}/processing-record` | 负责人保存 `cause`、`process`、`result`，其中 process 必填 |
| POST | `/work-orders/{id}/submit` | 负责人提交；`cause`、`process`、`result` 均必填 |
| POST | `/work-orders/{id}/retest` | 管理员触发待复测工单复检 |

工单操作可附带 `expectedState: {status, ownerAccountId, updatedAt}`，字段取自最近一次读取的工单，以避免覆盖他人的更新。状态流转见[告警流程图](系统架构与告警流程.md)。

示例创建请求：

```json
{"roomId":"从机房接口读取","title":"机柜温度告警处理","device":"A区机柜","alarmEvent":"从告警接口读取的id","priority":"紧急","note":"请核对冷通道送风"}
```

## 小维助手

前缀：`/assistant/rooms/{room}`，均要求管理员。建议为每个页面生成独立 `X-Assistant-Page`，格式为 1～64 位字母、数字、下划线或短横线，未提供时使用 `default`。

| 方法 | 子路径 | 说明 |
| --- | --- | --- |
| GET | `/session` | 当前页面会话 |
| POST | `/session/end` | 结束会话 |
| POST | `/chat` | `text`、`requestId` 必填；`stream: true` 启用 SSE |
| POST | `/confirm` | `pendingToken`、`kind`、`requestId`，均须对应当前确认卡片 |
| POST | `/confirmation/status` | 查询同一令牌、类型和请求 ID 的确认结果 |
| POST | `/pending/dismiss` | `pendingToken`，取消当前待确认操作 |
| POST | `/scene-result` | 回传浏览器场景动作结果，使用服务端下发的 token 与结果契约 |

`requestId` 应为每次逻辑请求生成的唯一标识；确认结果不确定时先查询状态，不能换一个请求 ID 自动重提写操作。SSE 响应为 `text/event-stream`，按 `event:` / `data:` 解析，结束事件为 `done`；不能按普通 JSON 一次性读取。

```json
{"text":"查询当前机房的未恢复告警","requestId":"客户端生成的唯一请求ID","stream":true}
```

更完整的工具与确认边界见[小维 Agent 当前架构与接口](小维Agent当前架构与接口.md)。
