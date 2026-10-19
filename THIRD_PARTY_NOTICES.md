# 第三方依赖与素材说明

根目录 MIT LICENSE 适用于本项目维护者有权授权的原创代码与文档，不改变第三方代码、素材和服务的原有授权。依赖的完整许可证请查看对应发行包与上游项目；本文件不是自动生成的完整依赖清单。

## 随仓库分发的 Three.js

`前端/smart-park-guat-web/src/views/home/scene/vendor/` 中的 Three.js r160 及 OrbitControls 来自 Three.js 项目。

- 上游：https://github.com/mrdoob/three.js/tree/r160
- Copyright © 2010-2023 three.js authors
- License：MIT；[上游完整许可](https://github.com/mrdoob/three.js/blob/r160/LICENSE)。保留现有源码版权头，分发时应同时保留完整许可。

## 视频与界面素材

- `public/videos/monitor-demo.mp4` 来自 Pexels，作者 ALL IZ Well，遵循 [Pexels License](https://www.pexels.com/license/)，不是将其改授 MIT。具体来源与加工方式见 [界面展示素材来源](docs/界面展示素材来源.md)。
- 本轮替换的五个菜单图标由内置 imagegen 生成；主题和制作说明见同一素材文档。
- 项目原有其他图片与美术资源尚无完整可核实的来源台账；不能仅凭根 LICENSE 认定维护者拥有其全部再授权权利。正式对外再分发前需补齐来源或替换为授权明确的素材。

## 包管理依赖与外部服务

Vue、Vite、Element Plus、ECharts、GSAP、HLS.js、Spring Boot、MyBatis、MySQL 驱动等按各自发行版本附带的许可使用；npm 和 Maven 依赖清单是当前依赖范围的依据，不能把所有依赖统一视为 MIT。

DeepSeek 等外部模型服务另受服务条款、账号权限与费用约束；根 LICENSE 不包含任何 API Key、服务额度或模型服务使用权。
