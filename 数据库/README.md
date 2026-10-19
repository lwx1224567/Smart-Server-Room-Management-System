# 数据库脚本说明

`platform_smart_park.sql` 和 `user.sql` 是 2025 年历史导出，不代表当前表结构，不用于当前数据库初始化或覆盖导入。其中 `station_id` 是历史园区关联命名；当前园区统一使用 `park_id`。

当前结构以 `../后端/smart-park-guat-server/database/migrations/` 和后端服务启动建表逻辑为准。命名规则及升级说明见 `ID命名规范.md`。

历史导出仅在本地保留，不随仓库发布。

