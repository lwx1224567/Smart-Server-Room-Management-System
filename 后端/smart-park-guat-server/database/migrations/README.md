# 阶段1台账迁移

V012__readable_audit_identifiers.sql 定义日志 RZ、请求 QQ、采集批次 PC、历史备份 BF、会话 HY 的公开编号注册表。ReadableIdentifierService 在应用就绪后幂等补齐历史编号，并检测及新增 batch_code / backup_code / session_code 列；新记录在保存时编号。原请求、鉴权及备份正文保持原用途，详见 `数据库/统一可读编号说明.md`。

V011__work_order_numbers.sql 新增工单公开编号映射与日流水表，格式为 GD + 创建日期 + 当日流水号。ConsoleService 启动时自动建表并补齐旧工单编号，原工单 ID 和通知关联不变。盘点及说明见 `数据库/工单编号与ID字段盘点.md`。

V010__alarm_numbers.sql 新增告警公开编号映射与日流水表。后端启动时自动建表并为历史告警补号，格式为 GJ + 创建日期 + 当日流水号；UUID 和工单关联保持原值。详见 `数据库/ID命名规范.md` 的告警公开编号说明。

ID 命名升级：V009 将工单负责人列统一为 `owner_account_id`，ConsoleService 启动时也会检测并执行同样的列改名。历史 V003 定义保留；旧工单 JSON 读取兼容，详见仓库 `数据库/ID命名规范.md`。

阶段1仅执行本目录 V001、V002 两份台账增量SQL，禁止执行仓库旧导出文件。五表按park→room→zone→cabinet→device创建，种子58条，全部DEMO/MOCK_IMPORT。无user写入；无告警、能耗、端口或工单。

后续角色后台另有 V003__console_schema.sql，记录 ConsoleService 启动时幂等创建的四张控制台表，不属于阶段1工具的迁移范围。账号迁移及工单/通知行为见 docs/项目说明/06-角色后台与顶部功能增量实现说明.md。

迁移工具`../tools/Stage1Database.java`从现有application.yml读取连接配置，不打印凭据。以JDK17和本地MySQL JDBC驱动运行：

```powershell
java --class-path <mysql-connector-j.jar> database/tools/Stage1Database.java . audit
java --class-path <mysql-connector-j.jar> database/tools/Stage1Database.java . test
java --class-path <mysql-connector-j.jar> database/tools/Stage1Database.java . migrate
java --class-path <mysql-connector-j.jar> database/tools/Stage1Database.java . verify
java --class-path <mysql-connector-j.jar> database/tools/Stage1Database.java . seed-check
```

`test`只在不存在的独立库platform_smart_park_stage1_test_20261008创建五表、执行两遍种子并验证。已存在则停止，保护数据；不自动清库。

`migrate`要求目标仍只有user且与本次批准的3条记录指纹一致，测试库验证通过后才创建目标五表。已有表或数据变化时停止，不强制覆盖。DDL不是可整体回滚事务；失败保留已建表，须人工复核后另行批准补齐。

`verify`只读检查正式目标数量、42柜坐标/模型键、9设备归属、4外键和user指纹。`seed-check`先验证完整目标，再重复种子并断言新增0行，事务失败rollback。

种子SQL仅按唯一业务键补缺，不能单独用于修复未知现状；执行前必须核对已有记录来源和归属。没有UPDATE、删除、忽略错误插入或覆盖写入。主键自增，不能把09中的示例ID作为固定ID。

已完成迁移：测试库和开发库各58条台账，开发库user仍3条。测试库保留供集成测试，未自动删除。

V006__maintenance_profiles.sql 为告警维修闭环新增的人员资料表，幂等建表、不改动原有账号与工单数据；ConsoleService 启动时自动执行同一 DDL。无需重跑 V001/V002 或导入旧导出。使用和重启步骤见 docs/项目说明/14-告警维修闭环实现与验证.md。

V007__assistant_session.sql 为按账号和机房保存的小维对话会话表；AssistantAgentService 启动时幂等创建，不回填或修改业务记录。启用步骤见 docs/项目说明/15-小维AI对话助手第一阶段实现与验证.md。

V008__unified_data_source.sql 新增来源迁移标记和原始记录快照表。UnifiedDataSourceMigration 在后端启动时自动将历史 DEMO 分类统一为 REAL，保留记录及关联，不重导种子。本文早期 DEMO 种子说明属于历史状态；新版本迁移与验收步骤见 docs/项目说明/17-统一数据来源实现与迁移.md。
