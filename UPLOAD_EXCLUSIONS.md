# GitHub 上传排除清单

本仓库以当前工作目录中的最新文件创建全新的初始提交，不上传旧 Git 历史。
所有排除文件仍保留在本机。

| 排除项 | 原因 |
| --- | --- |
| `简历.html` | 个人简历，按要求不上传 |
| `**/application-local.yml` | 本地数据库密码和外部服务密钥 |
| `**/.env`、`**/.env.*` | 本地环境配置；已检查的 `.env.development` 和示例 `.env.example` 例外 |
| `数据库/platform_smart_park.sql`、`数据库/user.sql` | 历史数据库导出，可能包含账号、密码和个人信息 |
| `.git-history-backup/` | 旧版本备份与本次上传快照，不上传历史或备份 |
| `node_modules/` | 可通过依赖清单安装的第三方依赖 |
| `target/`、`build/`、`dist/`、`dist-ssr/`、`*.class`、`*.tsbuildinfo` | 编译产物与缓存 |
| `logs/`、`*.log`、`test-results/`、`playwright-report/`、`coverage/` | 运行日志、测试报告，可能记录本地运行数据 |
| `docs/**/*.json`、`docs/**/*.txt` | 审计快照与执行证据，可能包含运行数据 |
| `docs/系统重新设计/00-现状调研/00-审计基线与环境清单.md` | 本机环境与审计基线信息 |
| `.vscode/`、`.idea/` 等 IDE 状态 | 本地编辑器配置 |
| `*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks`、`*.keystore` | 私钥和证书密钥容器 |

保留前后端源码、数据库结构迁移与演示种子脚本、依赖锁文件、测试代码、静态资源和普通项目说明。
数据库密码使用 `DB_PASSWORD`，外部服务密钥使用对应环境变量配置；真实值不应提交。
测试代码中的示例密码和 `13800000000` 为测试样例。
