# API接口现状报告

- 文档编号：AUD-0-06；版本：1.0（静态审计）
- 审计日期：2026-10-17（Asia/Shanghai）。
- 审计基线：分支 `master`，HEAD `565c762b0a49547ee60458b41c0f9a0e44a85f9d`，以当前未提交工作区为准；文件指纹见 00。
- 审计范围：当前所有Controller声明映射、参数/响应/调用入口/Service/表/权限/模拟/错误与副作用
- 证据规则：**事实**为源码/配置/DDL可直接确认；**推断**为基于代码的风险场景；**建议**不表示已经实施。未启动、未测试、未连接数据库，所有运行效果与实际数据状态均未验证。

## 现状事实、范围及核对方法

静态读取12个Controller的类级@RequestMapping与方法级@Get/Post/Put/Patch/DeleteMapping，拼接得到**61条映射**，其中业务/api/v1为60条，另/error为1条ANY映射。WeatherController.weather带synchronized亦纳入。没有执行应用/反射注册/OpenAPI或HTTP验证；Spring隐式HEAD/OPTIONS不另计，OPTIONS在Filter直接204。{room}与{roomId}均为路径变量命名，不形成不同资源。

当前源码未发现完全相同method+标准化URL的重复声明；功能重叠不等于路由冲突。Admin {kind}只允许parks/rooms/zones/cabinets/devices，下面列5条模板，不扩算为25条。所有表列省略公共身份查询表时，仍由ConsoleAccessFilter查console_account/session/person_profile。

## 共同契约、鉴权和错误处理

- 普通JSON返回R<T>：{code,msg,data,requestId,serverTime,error:{type,fields}|null}；成功code=200，空响应data=null。Java原始R/Map不构成字段级Schema，以下响应来自Service投影与前端类型交叉读取。
- E1：PlatformException按自身状态；非法参数400、重复409、数据库503、未知500；/error补404/405等。Filter认证失败直接R错误；Controller缺Authorization头由框架/Advice处理（未测具体异常分派）。
- E2：助手SSE开始后可能HTTP200内error事件，客户端必须等done才能视为完成；浏览器断开不回放写入。
- E3：天气外调失败仍R.success(data.available=false)；不是E1错误。
- E4：物理命令网关失败可R.success(data.status=FAILED)，调用方需检查业务状态。
- 权限真实边界以ConsoleAccessFilter+ConsoleService校验为准，不以Controller是否写@RequestHeader单独判断；没有JWT。

## 接口总矩阵

完整参数签名、响应及源码定位见紧随其后的逐接口卡片。调用方列是静态封装/页面入口，不表示运行已调用；管理配置中的动态path仍须按方法签名核对，未定位到独立UI的接口明确见卡片说明。

| 编号 | Method | URL | Controller.方法 | 权限 | 模拟/来源 | 错误 |
|---|---|---|---|---|---|---|
| API-01 | GET | /api/v1/admin/{kind} | AdminInventoryController.inventory | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-02 | GET | /api/v1/admin/{kind}/{id} | AdminInventoryController.item | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-03 | POST | /api/v1/admin/{kind} | AdminInventoryController.create | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-04 | PUT | /api/v1/admin/{kind}/{id} | AdminInventoryController.edit | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-05 | DELETE | /api/v1/admin/{kind}/{id} | AdminInventoryController.delete | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-06 | ANY | /error | ApiErrorController.error | 错误分派，Filter仅约束/api/v1 | 未见模拟返回 | E1 |
| API-07 | POST | /api/v1/assistant/rooms/{room}/session/end | AssistantAgentController.end | 已登录+ADMIN（Filter；部分Service再次校验） | 工具读数可混模拟；场景回执只代表可视化 | E1 |
| API-08 | GET | /api/v1/assistant/rooms/{room}/session | AssistantAgentController.current | 已登录+ADMIN（Filter；部分Service再次校验） | 工具读数可混模拟；场景回执只代表可视化 | E1 |
| API-09 | POST | /api/v1/assistant/rooms/{room}/chat | AssistantAgentController.chat | 已登录+ADMIN（Filter；部分Service再次校验） | 工具读数可混模拟；场景回执只代表可视化 | E1/E2 |
| API-10 | POST | /api/v1/assistant/rooms/{room}/scene-result | AssistantAgentController.scene | 已登录+ADMIN（Filter；部分Service再次校验） | 工具读数可混模拟；场景回执只代表可视化 | E1/E2 |
| API-11 | POST | /api/v1/auth/login | AuthController.login | 公开入口（logout仅撤销传入会话） | 未见模拟返回 | E1 |
| API-12 | GET | /api/v1/auth/me | AuthController.current | 已登录 | 未见模拟返回 | E1 |
| API-13 | POST | /api/v1/auth/logout | AuthController.logout | 公开入口（logout仅撤销传入会话） | 未见模拟返回 | E1 |
| API-14 | PUT | /api/v1/auth/password | AuthController.password | 已登录 | 未见模拟返回 | E1 |
| API-15 | GET | /api/v1/cabinets/{cabinetId} | CabinetController.cabinet | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-16 | GET | /api/v1/cabinets/{cabinetId}/devices | CabinetController.devices | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-17 | GET | /api/v1/integrations | IntegrationController.capabilities | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-18 | GET | /api/v1/integrations/collector | IntegrationController.collectorStatus | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-19 | POST | /api/v1/integrations/collector/pull | IntegrationController.collect | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-20 | POST | /api/v1/assistant/chat | IntegrationController.model | 已登录+ADMIN（Filter；部分Service再次校验） | 模型外调；probe成功回复固定模型文案 | E1 |
| API-21 | POST | /api/v1/rooms/{room}/ingest | IntegrationController.ingest | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-22 | POST | /api/v1/rooms/{room}/demo-monitoring/temperature | IntegrationController.demoTemperature | 已登录+ADMIN（Filter；部分Service再次校验） | 有：受控模拟温度为REAL | E1 |
| API-23 | GET | /api/v1/rooms/{room}/readings/history | IntegrationController.history | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-24 | GET | /api/v1/rooms/{room}/alarm-rules | IntegrationController.rules | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-25 | PUT | /api/v1/rooms/{room}/alarm-rules/{id} | IntegrationController.rule | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-26 | DELETE | /api/v1/rooms/{room}/alarm-rules/{id} | IntegrationController.deleteRule | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-27 | POST | /api/v1/rooms/{room}/alarms/evaluate | IntegrationController.evaluate | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-28 | PUT | /api/v1/rooms/{room}/cameras/{id}/video | IntegrationController.configureVideo | 已登录+ADMIN（Filter；部分Service再次校验） | 网关/配置；实际外部能力待核实 | E1 |
| API-29 | GET | /api/v1/rooms/{room}/cameras/{id}/video | IntegrationController.video | 已登录+ADMIN（Filter；部分Service再次校验） | 网关/配置；实际外部能力待核实 | E1 |
| API-30 | POST | /api/v1/rooms/{room}/cabinets/{id}/commands | IntegrationController.command | 已登录+ADMIN（Filter；部分Service再次校验） | 网关/配置；实际外部能力待核实 | E1/E4 |
| API-31 | GET | /api/v1/rooms/{room}/cabinets/{id}/commands | IntegrationController.commands | 已登录+ADMIN（Filter；部分Service再次校验） | 网关/配置；实际外部能力待核实 | E1 |
| API-32 | PUT | /api/v1/rooms/{roomId}/cabinets/{id}/controls | MonitoringController.control | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-33 | GET | /api/v1/rooms/{roomId}/monitoring | MonitoringController.snapshot | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-34 | GET | /api/v1/rooms/{roomId}/readings | MonitoringController.readings | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-35 | GET | /api/v1/rooms/{roomId}/alarms | MonitoringController.alarms | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-36 | GET | /api/v1/rooms/{roomId}/facilities | MonitoringController.facilities | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-37 | GET | /api/v1/rooms/{roomId}/network/topology | MonitoringController.topology | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-38 | GET | /api/v1/rooms/{roomId}/operation-logs | MonitoringController.logs | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-39 | GET | /api/v1/rooms/{roomId}/energy | MonitoringController.energy | 已登录+ADMIN（Filter；部分Service再次校验） | 有：自动补写模拟能耗为REAL | E1 |
| API-40 | PUT | /api/v1/rooms/{roomId}/alarms/{id}/acknowledge | MonitoringController.acknowledge | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-41 | GET | /api/v1/notifications | NotificationController.notices | 已登录；通知限本人 | 未见模拟返回 | E1 |
| API-42 | PUT | /api/v1/notifications/{id}/read | NotificationController.read | 已登录；通知限本人 | 未见模拟返回 | E1 |
| API-43 | DELETE | /api/v1/notifications/read | NotificationController.deleteRead | 已登录；通知限本人 | 未见模拟返回 | E1 |
| API-44 | GET | /api/v1/rooms | RoomController.rooms | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-45 | GET | /api/v1/rooms/{roomId} | RoomController.room | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-46 | GET | /api/v1/rooms/{roomId}/scene | RoomController.scene | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-47 | GET | /api/v1/rooms/{roomId}/cabinets | RoomController.cabinets | 已登录+ADMIN（Filter；部分Service再次校验） | 数据库可能含种子/模拟/REAL归一数据，未核实实例 | E1 |
| API-48 | GET | /api/v1/users | UserController.people | 已登录+ADMIN（Filter；部分Service再次校验） | 未见模拟返回 | E1 |
| API-49 | POST | /api/v1/users | UserController.add | 已登录+ADMIN（Filter；部分Service再次校验） | 未见模拟返回 | E1 |
| API-50 | PUT | /api/v1/users/{id} | UserController.edit | 已登录+ADMIN（Filter；部分Service再次校验） | 未见模拟返回 | E1 |
| API-51 | DELETE | /api/v1/users/{id} | UserController.delete | 已登录+ADMIN（Filter；部分Service再次校验） | 未见模拟返回 | E1 |
| API-52 | GET | /api/v1/weather | WeatherController.weather | 已登录；通知限本人 | 外部天气；失败available=false | E1/E3 |
| API-53 | GET | /api/v1/work-orders | WorkOrderController.orders | 已登录；维保仅本人/未指派工单 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-54 | GET | /api/v1/work-orders/history | WorkOrderController.history | 已登录+Service管理员 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-55 | POST | /api/v1/work-orders | WorkOrderController.create | 已登录+Service管理员 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-56 | POST | /api/v1/work-orders/{id}/claim | WorkOrderController.claim | 已登录+维保且可领取 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-57 | POST | /api/v1/work-orders/{id}/assign | WorkOrderController.assign | 已登录+Service管理员 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-58 | POST | /api/v1/work-orders/{id}/start | WorkOrderController.start | 已登录+当前负责人 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-59 | PATCH | /api/v1/work-orders/{id}/processing-record | WorkOrderController.save | 已登录+当前负责人 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-60 | POST | /api/v1/work-orders/{id}/submit | WorkOrderController.submit | 已登录+当前负责人 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |
| API-61 | POST | /api/v1/work-orders/{id}/retest | WorkOrderController.retest | 已登录+Service管理员 | 工单真实操作；复检可采纳归一后的模拟样本 | E1 |

## 逐接口参数、响应、服务与表证据

### API-01 GET /api/v1/admin/{kind}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java:13`，方法 `inventory`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String kind,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="100")int pageSize`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {items:camelCase行[],total,page,pageSize}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue。
- Service：InventoryMaintenanceService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；删除检查sp_reading/sp_monitor_record/sp_energy_sample/console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-02 GET /api/v1/admin/{kind}/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java:18`，方法 `item`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String kind,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = camelCase台账行（JDBC ID为数值）；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue。
- Service：InventoryMaintenanceService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；删除检查sp_reading/sp_monitor_record/sp_energy_sample/console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-03 POST /api/v1/admin/{kind}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java:19`，方法 `create`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String kind,@RequestBody Map<String,Object>b`。
- 请求结构：按kind允许camelCase字段；create/edit采用同一save白名单，细项见下文。
- 响应：R.data = camelCase新建行；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue。
- Service：InventoryMaintenanceService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；删除检查sp_reading/sp_monitor_record/sp_energy_sample/console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-04 PUT /api/v1/admin/{kind}/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java:20`，方法 `edit`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String kind,@PathVariable String id,@RequestBody Map<String,Object>b`。
- 请求结构：按kind允许camelCase字段；create/edit采用同一save白名单，细项见下文。
- 响应：R.data = camelCase更新行；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue。
- Service：InventoryMaintenanceService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；删除检查sp_reading/sp_monitor_record/sp_energy_sample/console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-05 DELETE /api/v1/admin/{kind}/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java:21`，方法 `delete`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String kind,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue。
- Service：InventoryMaintenanceService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；删除检查sp_reading/sp_monitor_record/sp_energy_sample/console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-06 ANY /error

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/ApiErrorController.java:14`，方法 `error`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`HttpServletRequest request`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：HTTP错误状态+R.error，type NOT_FOUND/METHOD_NOT_ALLOWED/HTTP_ERROR；Java声明 `ResponseEntity<R>`。
- 调用方：Servlet容器/任意未知请求。
- Service：无；Servlet错误分派；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：无。
- 鉴权：错误分派，Filter仅约束/api/v1。模拟数据：未见模拟返回。
- 错误：E1。
### API-07 POST /api/v1/assistant/rooms/{room}/session/end

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AssistantAgentController.java:23`，方法 `end`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room`。
- 请求结构：无业务body；结束当前账号/机房/页面会话。
- 响应：R.data = 空对象{}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/home/assistant/agent-client.ts。
- Service：AssistantAgentService；ConsoleService.identity/admin；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：会话内存；查询sp_*和console_*；确认动作写console_order/notice及告警。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：工具读数可混模拟；场景回执只代表可视化。
- 错误：E1。
### API-08 GET /api/v1/assistant/rooms/{room}/session

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AssistantAgentController.java:24`，方法 `current`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {reply,messages,context,pending,trace,clientAction,interrupted}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/home/assistant/agent-client.ts。
- Service：AssistantAgentService；ConsoleService.identity/admin；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：会话内存；查询sp_*和console_*；确认动作写console_order/notice及告警。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：工具读数可混模拟；场景回执只代表可视化。
- 错误：E1。
### API-09 POST /api/v1/assistant/rooms/{room}/chat

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AssistantAgentController.java:25`，方法 `chat`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response`。
- 请求结构：body含{text,requestId,pendingToken?,reset?,sceneCabinet?,stream?}。
- 响应：R.data = 普通R<AgentSession>或SSE status/token等事件+done(AgentSession)/error；Java声明 `Object`。
- 调用方：前端/smart-park-guat-web/src/views/home/assistant/agent-client.ts。
- Service：AssistantAgentService；ConsoleService.identity/admin；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：会话内存；查询sp_*和console_*；确认动作写console_order/notice及告警。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：工具读数可混模拟；场景回执只代表可视化。
- 错误：E1+E2。
### API-10 POST /api/v1/assistant/rooms/{room}/scene-result

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AssistantAgentController.java:26`，方法 `scene`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response`。
- 请求结构：body含{token,result,arrived?,cabinetSceneModelKey?,stream?}。
- 响应：R.data = 普通R<AgentSession>或SSE，同chat；Java声明 `Object`。
- 调用方：前端/smart-park-guat-web/src/views/home/assistant/agent-client.ts。
- Service：AssistantAgentService；ConsoleService.identity/admin；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：会话内存；查询sp_*和console_*；确认动作写console_order/notice及告警。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：工具读数可混模拟；场景回执只代表可视化。
- 错误：E1+E2。
### API-11 POST /api/v1/auth/login

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AuthController.java:14`，方法 `login`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestBody Map<String,Object>b`。
- 请求结构：body={userName,password}；仅字段名，不记录秘密值。
- 响应：R.data = {token,user:{id,userName,displayName,role,enabled,phone,email,biography,skills}}（不记录token值）；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/console.ts。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_session；publicUser读console_person_profile。
- 鉴权：公开入口（logout仅撤销传入会话）。模拟数据：未见模拟返回。
- 错误：E1。
### API-12 GET /api/v1/auth/me

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AuthController.java:15`，方法 `current`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader(value="Authorization",required=false)String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 公开用户对象；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/console.ts。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_session；publicUser读console_person_profile。
- 鉴权：已登录。模拟数据：未见模拟返回。
- 错误：E1。
### API-13 POST /api/v1/auth/logout

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AuthController.java:16`，方法 `logout`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader(value="Authorization",required=false)String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/console.ts。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_session；publicUser读console_person_profile。
- 鉴权：公开入口（logout仅撤销传入会话）。模拟数据：未见模拟返回。
- 错误：E1。
### API-14 PUT /api/v1/auth/password

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/AuthController.java:17`，方法 `password`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestBody Map<String,Object>b`。
- 请求结构：body={oldPassword,newPassword}。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/console.ts。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_session；publicUser读console_person_profile。
- 鉴权：已登录。模拟数据：未见模拟返回。
- 错误：E1。
### API-15 GET /api/v1/cabinets/{cabinetId}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/CabinetController.java:11`，方法 `cabinet`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String cabinetId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = Cabinet详情+运行读数；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_cabinet/sp_zone/sp_room/sp_park/sp_device；sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-16 GET /api/v1/cabinets/{cabinetId}/devices

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/CabinetController.java:12`，方法 `devices`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String cabinetId,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String type,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = Page<Device>；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_cabinet/sp_zone/sp_room/sp_park/sp_device；sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-17 GET /api/v1/integrations

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:12`，方法 `capabilities`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`无`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {agentEnabled,modelConfigured,videoGatewayConfigured,controlGatewayConfigured}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：仅配置。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-18 GET /api/v1/integrations/collector

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:13`，方法 `collectorStatus`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`无`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {configured,lastRun}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：仅配置与内存lastCollection。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-19 POST /api/v1/integrations/collector/pull

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:14`，方法 `collect`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`无`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 采集批次处理结果；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_ingest_batch/sp_reading/sp_energy_sample/sp_monitor_record；校验sp_room/cabinet/device；评估读console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-20 POST /api/v1/assistant/chat

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:15`，方法 `model`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestBody Map<String,Object>b`。
- 请求结构：body={text,messages?,roomId?,contextId?,probeModel?}。
- 响应：R.data = {action:{reply或type/operation等},modelCall?}，probe分支回复有固定文案；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_cabinet/sp_zone/sp_monitor_record/console_account；外部模型。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：模型外调；probe成功回复固定模型文案。
- 错误：E1。
### API-21 POST /api/v1/rooms/{room}/ingest

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:16`，方法 `ingest`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@RequestBody Map<String,Object>b`。
- 请求结构：body={batchId,readings?[],energy?[],records?[]}；非空；各数组最多2000。
- 响应：R.data = {duplicate,batchId,readings?,energy?,records?,alarms?}；Java声明 `R`。
- 调用方：仓库未定位独立页面直接调用；供外部接入/管理使用的意图需核实。相关封装/配置入口：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_ingest_batch/sp_reading/sp_energy_sample/sp_monitor_record；台账归属；console_order。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-22 POST /api/v1/rooms/{room}/demo-monitoring/temperature

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:17`，方法 `demoTemperature`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b`。
- 请求结构：body严格仅{cabinetId:string,value:0..100,requestId:字母数字_-长度1..64}。
- 响应：R.data = {duplicate,requestId,roomId,cabinetId,readingId,value,unit,observedAt,quality,recordOrigin,source,ruleId,alarmIds}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_zone/sp_cabinet/sp_reading/sp_ingest_batch/sp_monitor_record。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：有：受控模拟温度为REAL。
- 错误：E1。
### API-23 GET /api/v1/rooms/{room}/readings/history

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:18`，方法 `history`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@RequestParam String entityType,@RequestParam String entityId,@RequestParam String metric,@RequestParam String from,@RequestParam String to`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = [{metric,value,unit,observedAt,quality,recordOrigin,source}]，最多2000；Java声明 `R`。
- 调用方：仓库未定位独立页面直接调用；供外部接入/管理使用的意图需核实。相关封装/配置入口：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_reading及台账归属验证。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-24 GET /api/v1/rooms/{room}/alarm-rules

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:19`，方法 `rules`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = RULE payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(RULE)。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-25 PUT /api/v1/rooms/{room}/alarm-rules/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:20`，方法 `rule`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b`。
- 请求结构：body={entityType,entityId,metric,title,unit,min,max,maxAgeSeconds,normalSeconds,maxGapSeconds,severity,enabled,recordOrigin?,sampleSource?,advice?}。
- 响应：R.data = 保存后的RULE payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(RULE)+台账校验。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-26 DELETE /api/v1/rooms/{room}/alarm-rules/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:21`，方法 `deleteRule`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(RULE)。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-27 POST /api/v1/rooms/{room}/alarms/evaluate

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:22`，方法 `evaluate`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@RequestParam(defaultValue="REAL")String recordOrigin`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 变更告警计数(int)；Java声明 `R`。
- 调用方：仓库未定位独立页面直接调用；供外部接入/管理使用的意图需核实。相关封装/配置入口：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(RULE/ALARM)、sp_reading、console_order、sp_room/device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-28 PUT /api/v1/rooms/{room}/cameras/{id}/video

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:23`，方法 `configureVideo`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b`。
- 请求结构：body={mode:DIRECT/GATEWAY,url或channelId,protocol:MP4/HLS,...}。
- 响应：R.data = VIDEO配置对象；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(FACILITY/VIDEO)。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：网关/配置；实际外部能力待核实。
- 错误：E1。
### API-29 GET /api/v1/rooms/{room}/cameras/{id}/video

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:24`，方法 `video`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {url,protocol,live,...网关字段}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(FACILITY/VIDEO)+外部网关。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：网关/配置；实际外部能力待核实。
- 错误：E1。
### API-30 POST /api/v1/rooms/{room}/cabinets/{id}/commands

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:25`，方法 `command`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b`。
- 请求结构：body={key:lighting/fans/locked/power/doorOpen,value:boolean}。
- 响应：R.data = {id,roomId,cabinetId,key,value,status,actualValue?,message?,accountId,createdAt,...}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(COMMAND/CONTROL/LOG)、sp_reading、台账+外部网关。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：网关/配置；实际外部能力待核实。
- 错误：E1+E4。
### API-31 GET /api/v1/rooms/{room}/cabinets/{id}/commands

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/IntegrationController.java:26`，方法 `commands`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String room,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = COMMAND payload[]；Java声明 `R`。
- 调用方：仓库未定位独立页面直接调用；供外部接入/管理使用的意图需核实。相关封装/配置入口：前端/smart-park-guat-web/src/api/platform/integrations.ts；前端/smart-park-guat-web/src/components/ConsoleSettings.vue；前端/smart-park-guat-web/src/views/home/assistant/assistant-model.ts。
- Service：IntegrationService（温度为DemoMonitoringService）；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_monitor_record(COMMAND)+台账。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：网关/配置；实际外部能力待核实。
- 错误：E1。
### API-32 PUT /api/v1/rooms/{roomId}/cabinets/{id}/controls

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:13`，方法 `control`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> body`。
- 请求结构：body={key:lighting/fans/locked/power,value:boolean}。
- 响应：R.data = {controls,log}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-33 GET /api/v1/rooms/{roomId}/monitoring

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:14`，方法 `snapshot`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {roomId,generatedAt,readings,alarms,facilities,events,nodes,links,logs,controls}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-34 GET /api/v1/rooms/{roomId}/readings

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:15`，方法 `readings`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = MonitorReading[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-35 GET /api/v1/rooms/{roomId}/alarms

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:16`，方法 `alarms`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = ALARM payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-36 GET /api/v1/rooms/{roomId}/facilities

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:17`，方法 `facilities`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = FACILITY payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-37 GET /api/v1/rooms/{roomId}/network/topology

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:18`，方法 `topology`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {roomId,nodes,links}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-38 GET /api/v1/rooms/{roomId}/operation-logs

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:19`，方法 `logs`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = LOG payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-39 GET /api/v1/rooms/{roomId}/energy

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:20`，方法 `energy`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId,@RequestParam(defaultValue="today")String period`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {labels,totals,total,shares,ranks,available,recordOrigin,simulated,partial}；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：有：自动补写模拟能耗为REAL。
- 错误：E1。
### API-40 PUT /api/v1/rooms/{roomId}/alarms/{id}/acknowledge

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/MonitoringController.java:21`，方法 `acknowledge`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId,@PathVariable String id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 更新后的ALARM payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/stores/monitoring.ts；前端/smart-park-guat-web/src/views/home/assistant/alarm-orders.ts；前端/smart-park-guat-web/src/api/platform/client.ts。
- Service：MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_room/sp_monitor_record/sp_reading；energy另读写sp_energy_sample、读sp_cabinet/sp_device。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-41 GET /api/v1/notifications

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/NotificationController.java:14`，方法 `notices`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = [{id,title,content,orderId,isRead,createdAt}]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/components/ConsoleNotices.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_notice；身份读console_account/console_session/person_profile。
- 鉴权：已登录；通知限本人。模拟数据：未见模拟返回。
- 错误：E1。
### API-42 PUT /api/v1/notifications/{id}/read

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/NotificationController.java:15`，方法 `read`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@PathVariable Long id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/components/ConsoleNotices.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_notice；身份读console_account/console_session/person_profile。
- 鉴权：已登录；通知限本人。模拟数据：未见模拟返回。
- 错误：E1。
### API-43 DELETE /api/v1/notifications/read

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/NotificationController.java:16`，方法 `deleteRead`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/components/ConsoleNotices.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_notice；身份读console_account/console_session/person_profile。
- 鉴权：已登录；通知限本人。模拟数据：未见模拟返回。
- 错误：E1。
### API-44 GET /api/v1/rooms

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/RoomController.java:11`，方法 `rooms`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="REAL") String recordOrigin,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {items:Room[],total,page,pageSize,meta}；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；场景/运行读数关联sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-45 GET /api/v1/rooms/{roomId}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/RoomController.java:12`，方法 `room`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = Room+zoneCount/cabinetCount/registeredDeviceCount；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；场景/运行读数关联sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-46 GET /api/v1/rooms/{roomId}/scene

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/RoomController.java:13`，方法 `scene`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId,@RequestParam(defaultValue="REAL") String recordOrigin`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = SceneSnapshot(roomId,zones,cabinets,devices,meta等；见Service投影)；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；场景/运行读数关联sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-47 GET /api/v1/rooms/{roomId}/cabinets

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/RoomController.java:14`，方法 `cabinets`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@PathVariable String roomId,@RequestParam(required=false) String zoneId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = Page<Cabinet>；Java声明 `R<Object>`。
- 调用方：前端/smart-park-guat-web/src/api/platform/inventory.ts。
- Service：InventoryService→InventoryMapper/MonitoringService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：sp_park/sp_room/sp_zone/sp_cabinet/sp_device；场景/运行读数关联sp_reading。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：数据库可能含种子/模拟/REAL归一数据，未核实实例。
- 错误：E1。
### API-48 GET /api/v1/users

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/UserController.java:14`，方法 `people`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 公开维保人员对象[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/user/index.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_person_profile/console_session；删除检查console_order并删notice、兼容旧user。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：未见模拟返回。
- 错误：E1。
### API-49 POST /api/v1/users

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/UserController.java:15`，方法 `add`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@RequestBody Map<String,Object>b`。
- 请求结构：body={userName,displayName,password,phone?,email?,enabled?,biography?,skills?}；创建角色固定MAINTAINER。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/user/index.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_person_profile/console_session；删除检查console_order并删notice、兼容旧user。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：未见模拟返回。
- 错误：E1。
### API-50 PUT /api/v1/users/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/UserController.java:16`，方法 `edit`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@PathVariable Long id,@RequestBody Map<String,Object>b`。
- 请求结构：body={displayName,password?,phone?,email?,enabled?,biography?,skills?}；userName不用于修改。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/user/index.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_person_profile/console_session；删除检查console_order并删notice、兼容旧user。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：未见模拟返回。
- 错误：E1。
### API-51 DELETE /api/v1/users/{id}

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/UserController.java:17`，方法 `delete`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h,@PathVariable Long id`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 空data=null；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/views/user/index.vue。
- Service：ConsoleService；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_account/console_person_profile/console_session；删除检查console_order并删notice、兼容旧user。
- 鉴权：已登录+ADMIN（Filter；部分Service再次校验）。模拟数据：未见模拟返回。
- 错误：E1。
### API-52 GET /api/v1/weather

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WeatherController.java:22`，方法 `weather`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String h`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = {city,temperature,code,updatedAt,available,provider}或{city,latitude,longitude,available:false,message}；Java声明 `synchronized R`。
- 调用方：前端/smart-park-guat-web/src/services/weather.ts。
- Service：ConsoleService.identity；Controller内HTTP；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：身份表；天气来自Open-Meteo内存缓存。
- 鉴权：已登录；通知限本人。模拟数据：外部天气；失败available=false。
- 错误：E1+E3。
### API-53 GET /api/v1/work-orders

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:15`，方法 `orders`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 工单payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录；维保仅本人/未指派工单。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-54 GET /api/v1/work-orders/history

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:16`，方法 `history`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@RequestParam String roomId,@RequestParam(required=false)String cabinetId,@RequestParam(required=false)String deviceId,@RequestParam(required=false)String recordOrigin`。
- 请求结构：无额外JSON body，参数与默认值以签名为准。
- 响应：R.data = 已完成工单payload[]；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+Service管理员。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-55 POST /api/v1/work-orders

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:17`，方法 `create`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> body`。
- 请求结构：body={title,device,roomId?,alarmEvent?,cabinetId?,deviceId?,recordOrigin?,source?,priority?,note?}；缺roomId选首机房；告警字段由后端覆盖。
- 响应：R.data = 新建或复用未完成工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+Service管理员。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-56 POST /api/v1/work-orders/{id}/claim

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:18`，方法 `claim`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body`。
- 请求结构：Action={expectedState?}；POST需可解析JSON对象。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+维保且可领取。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-57 POST /api/v1/work-orders/{id}/assign

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:19`，方法 `assign`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Assignment body`。
- 请求结构：Assignment={ownerAccountId,expectedState?}。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+Service管理员。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-58 POST /api/v1/work-orders/{id}/start

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:20`，方法 `start`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body`。
- 请求结构：Action={expectedState?}；POST需可解析JSON对象。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+当前负责人。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-59 PATCH /api/v1/work-orders/{id}/processing-record

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:21`，方法 `save`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody ProcessingRecord body`。
- 请求结构：ProcessingRecord={cause?,process,result?,expectedState?}。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+当前负责人。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-60 POST /api/v1/work-orders/{id}/submit

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:22`，方法 `submit`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Submission body`。
- 请求结构：Submission={cause,process,result,expectedState?}。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+当前负责人。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。
### API-61 POST /api/v1/work-orders/{id}/retest

- Controller证据：`后端/smart-park-guat-server/src/main/java/cn/guat/smartpark/controller/WorkOrderController.java:23`，方法 `retest`。
- 参数原始签名（含Path/Query/Header/defaultValue）：`@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body`。
- 请求结构：Action={expectedState?}；POST需可解析JSON对象。
- 响应：R.data = 动作后工单payload；Java声明 `R`。
- 调用方：前端/smart-park-guat-web/src/services/work-orders.ts；前端/smart-park-guat-web/src/components/work-orders/WorkOrderCenter.vue；前端/smart-park-guat-web/src/views/maintenance/index.vue。
- Service：ConsoleService；MonitoringService复检；具体调用以Controller方法及Service同名/委托方法为准。
- 对应表：console_order/console_account/console_notice；sp_room/sp_monitor_record/sp_reading。
- 鉴权：已登录+Service管理员。模拟数据：工单真实操作；复检可采纳归一后的模拟样本。
- 错误：E1。

## 字段级补充与服务方法对应

Admin维护：kind=parks:{code,name}；rooms:{parkId,code,name,location,managerDisplayName?,sceneModelKey}；zones:{roomId,code,name,displayOrder}；cabinets:{zoneId,code,name,serialNumber,capacityU,sceneModelKey,gridRow,gridColumn,positionX/Y/Z,registrationStatus}；devices:{cabinetId,code,name,deviceType,sceneModelKey,legacyKey,registrationStatus,uStart?,uHeight?,networkLayer?}；均可带recordOrigin。Controller create/edit→save，delete→delete，inventory→list，item→get。

Integration：collectorStatus→collectorStatus、collect→collect、model→model、ingest→ingest、demoTemperature→demo.submit、history→history、rules→rules、rule→saveRule、deleteRule→deleteRule、evaluate→evaluate、configureVideo→videoConfig、video→video、command→command、commands→commands。Monitoring：snapshot→snapshot、readings→readings、alarms/facilities/logs/topology→records(各kind)、control→control、energy→energy、acknowledge→acknowledge。Auth current→identity，password→changePassword；User add/edit→savePerson、delete→deletePerson；WorkOrder所有动作→action，create→createOrder；Notification read→readNotice、deleteRead→deleteRead；Assistant scene→sceneResult、chat→chat/stream、current→current、end→end。

采集数组字段：readings={entityType,entityId,metric,value,unit,observedAt,quality,source,recordOrigin}；energy={category,cabinetId?,kwh,observedAt,quality,source,recordOrigin}；records={kind,id,observedAt,source,recordOrigin,cabinetId?,deviceId?,...类型payload}，kind白名单FACILITY/EVENT/NODE/LINK/LOG。业务响应payload关键形状见05/07与前端types。动态JSON未知扩展字段不编造为已定义契约。

## 已确认问题及兼容性

1. GET energy有数据库补写；GET video网关模式也可能发生外部POST（服务端换取播放地址），不得把所有GET当无副作用探测（R02）。
2. controls与commands字段不完全一致，前者仅平台状态，后者设备回执；assistant/chat与assistant/rooms/{room}/chat两代模型/工具入口并存，不能混用语义（R05）。
3. 查询台账string ID与admin数值ID；普通请求丢弃结构化error/requestId；SSE/业务FAILED/available=false是不同失败协议（R08/R13）。
4. recordOrigin仍允许DEMO参数但查询/输出归一且rooms未按该参数实际过滤；是兼容别名而不是独立来源筛选（R01）。
5. 手工校验覆盖范围不同：多数Map允许额外字段，模拟温度严格只允许3字段；无统一Bean Validation，字段长度/日期/必填需逐契约收敛（R14）。
6. 工单create roomId缺省选首房；Admin分页与readings全量、history最多2000且未给截断游标；并发/大数据下兼容影响待验证（R09/R12）。

## 改进建议

阶段1形成稳定ID/单位/时间/分页/错误和数据来源契约字典，区分读取、平台状态写入、真实命令与业务动作。阶段2再制定旧接口适配、弃用规则、Schema与契约测试方案；本次不修改接口。

## 待确认事项

运行实例真实路由、外部调用方、反向代理前缀、客户端版本、请求/响应样本、接口SLA和容量。仅静态调用入口已识别；无法证明仓库外是否有调用方。

工作记录：逐Controller读取并与注解提取清单交叉核对，61条均附原始参数签名及返回说明；未执行任何接口验证。

