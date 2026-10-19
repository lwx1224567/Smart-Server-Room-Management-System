# 前后端项目结构详解（供 GPT 审查）

> 扫描日期：2026-10-17。基于当前磁盘工作区，包含未提交及未跟踪文件；不把 Git 已删除文件当作现存文件。静态结构梳理，未运行应用、构建或测试。

## 1. 审查背景与范围

这是一个智慧园区/机房管理项目，包含台账、三维机房、监测告警、能耗、网络拓扑、工单、账号权限及 AI 巡检助手。下文给出逐文件清单和从源码提取的依赖/入口，帮助审查职责、模块边界、可维护性及目录命名。

主目录为 `前端/smart-park-guat-web`、`后端/smart-park-guat-server`、`数据库`。根目录还有 `.gitignore`、`.vscode`、`docs`、`简历.html`；其中简历与业务应用无直接结构关系。`docs/系统重新设计/00-现状调研` 已有历史审计报告，本清单以当前磁盘为准。

排除 npm 依赖、Maven 编译产物、打包产物、IDE 元数据和测试结果目录；所有纳入范围的文件（包括每张序列帧）逐项列出。本地 vendor 中的 Three.js 和 OrbitControls 保留在清单中。文件用途说明为人工概括或按文件位置归类，源码标识和依赖由静态正则提取，不能替代完整语义分析。

## 2. 技术与组织方式

- 前端：Vue 3、TypeScript、Vite 7、Pinia、Vue Router、Element Plus、ECharts、GSAP；三维运行库在 scene/vendor 本地保存。部分业务仍使用 JavaScript。package.json 也声明 axios，主控制台请求封装使用 fetch。
- 后端：Spring Boot 2.6.13，Maven，Java 8 编译目标，MyBatis、JdbcTemplate、MySQL、Lombok。版本来自仓库配置，未查询外部最新版本。
- 路由：/home、/user 使用 Layout；/maintenance 独立；/login 登录；首页驾驶舱通过页面状态切换，并非每个驾驶舱均有独立 URL。
- 认证与 HTTP：router → services/console.ts → API；助手通过 agent-client.ts 单独处理 SSE。服务端由 ConsoleAccessFilter 处理访问过滤。

## 3. 主要业务链路

```text
main.ts → App.vue → router → layouts / views
home/index.vue → composables → stores/platform、stores/monitoring → api/platform → services/console.ts
stores → useHomeSceneData → platform-scene-adapter → RoomScene.vue → scene.ts → scene/vendor
InspectionAssistant → agent-* → AssistantAgentController → AssistantAgentService → 业务工具 / IntegrationService
Controller → InventoryService → InventoryMapper → 数据库
其他业务 Controller → Console/Monitoring/Integration 等 Service → JdbcTemplate / 外部系统
```

## 4. 结构审查关注点（建议，不是已验证故障）

1. ConsoleService、IntegrationService、AssistantAgentService 和 scene.ts 的体积与职责；结合后文行数及方法，判断是否需要按领域拆分。
2. service 目录同时包含过滤器、调度、数据迁移和领域服务，是否应区分 web/security、application、infrastructure 等职责。
3. MyBatis 与 JdbcTemplate 并存，SQL 和业务逻辑边界是否一致；DTO、Map 响应及前端类型是否形成统一契约。
4. 前端 Pinia 与 services 中的模块级响应式状态并存，认证、工单、监测各自由谁管理；API 封装与 SSE 封装的共性是否清晰。
5. home 同时承载驾驶舱、场景与助手，业务间通过 imports 和状态关联，是否能独立演进；shared 组件是否放在实际复用范围内。
6. 根数据库目录、后端 migrations、维护脚本和启动迁移的职责及执行顺序是否明确。
7. JS/TS 混合、本地 vendor、演示数据与正式数据的边界，以及大量序列帧对维护和打包的影响。
8. 测试文件不少，但目录数量不能证明覆盖有效；还应核对脚本入口、测试隔离、关键业务验收和 CI。

## 前端逐文件清单

相对根目录：`前端/smart-park-guat-web`；共 398 个文件。

目录层级（文件完整路径见逐项清单）：

```text
前端/smart-park-guat-web/
  └─ e2e/
    └─ platform/
  └─ public/
    └─ videos/
  └─ scripts/
  └─ src/
    └─ api/
      └─ platform/
    └─ assets/
      └─ business-menu/
      └─ css/
      └─ icon/
      └─ layouts/
      └─ monitoring/
    └─ components/
      └─ Border3/
        └─ assets/
        └─ sequence/
          └─ bg/
      └─ BusinessMenu/
      └─ work-orders/
    └─ layouts/
      └─ components/
        └─ menu/
          └─ src/
        └─ top/
          └─ sequence/
            └─ topbg/
    └─ router/
    └─ services/
    └─ stores/
    └─ types/
    └─ utils/
    └─ views/
      └─ home/
        └─ assistant/
        └─ cockpit/
          └─ alarms/
            └─ components/
          └─ assets/
            └─ components/
            └─ composables/
            └─ styles/
          └─ cabinet/
            └─ components/
            └─ shared/
          └─ energy/
            └─ components/
            └─ shared/
          └─ room/
            └─ components/
          └─ topology/
            └─ components/
        └─ components/
        └─ composables/
        └─ demo/
        └─ scene/
          └─ vendor/
        └─ styles/
      └─ login/
      └─ maintenance/
      └─ user/
  └─ tests/
    └─ platform/
```

### .

- `.env.development` — 前端环境配置，仅列文件名，不复制变量值。约 8 行。
- `.gitattributes` — Git 文件属性和换行规则。约 2 行。
- `.gitignore` — Git 忽略规则。约 32 行。
- `.prettierignore` — Prettier 格式配置或忽略规则。约 10 行。
- `.prettierrc.json` — Prettier 格式配置或忽略规则。约 7 行。
### e2e/platform

- `e2e/platform/a1-final.spec.ts` — 浏览器端到端测试或公共夹具：a1-final.spec.ts。约 77 行。
- `e2e/platform/a1-identity.spec.ts` — 浏览器端到端测试或公共夹具：a1-identity.spec.ts。约 199 行。
  - 公开方法/导出标识：`open`、`business`、`choose`。
- `e2e/platform/agent-session.spec.ts` — 浏览器端到端测试或公共夹具：agent-session.spec.ts。约 22 行。
  - 本地依赖：`../../tests/platform/agent-test-fixture`。
- `e2e/platform/agent-stream.spec.ts` — 浏览器端到端测试或公共夹具：agent-stream.spec.ts。约 109 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/api-contract.spec.ts` — 浏览器端到端测试或公共夹具：api-contract.spec.ts。约 70 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/assistant-dialogue.spec.ts` — 浏览器端到端测试或公共夹具：assistant-dialogue.spec.ts。约 34 行。
  - 本地依赖：`../../tests/platform/agent-test-fixture`。
- `e2e/platform/assistant-markdown.spec.ts` — 浏览器端到端测试或公共夹具：assistant-markdown.spec.ts。约 79 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/assistant-visit.spec.ts` — 浏览器端到端测试或公共夹具：assistant-visit.spec.ts。约 100 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/business-routing.spec.ts` — 浏览器端到端测试或公共夹具：business-routing.spec.ts。约 38 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/cabinet-inventory.spec.ts` — 浏览器端到端测试或公共夹具：cabinet-inventory.spec.ts。约 56 行。
  - 公开方法/导出标识：`select`、`models`。
- `e2e/platform/console-fixture.ts` — 浏览器端到端测试或公共夹具：console-fixture.ts。约 195 行。
  - 公开方法/导出标识：`mockConsole`、`loginAs`。
- `e2e/platform/console.spec.ts` — 浏览器端到端测试或公共夹具：console.spec.ts。约 89 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/home-structure.spec.ts` — 浏览器端到端测试或公共夹具：home-structure.spec.ts。约 171 行。
  - 本地依赖：`./console-fixture`、`../../src/views/home/demo/mockData.js`。
  - 公开方法/导出标识：`setupHome`。
- `e2e/platform/maintenance-backend.spec.ts` — 浏览器端到端测试或公共夹具：maintenance-backend.spec.ts。约 107 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/maintenance-closure.spec.ts` — 浏览器端到端测试或公共夹具：maintenance-closure.spec.ts。约 59 行。
  - 本地依赖：`./console-fixture`、`../../tests/platform/agent-test-fixture`。
- `e2e/platform/model-probe.spec.ts` — 浏览器端到端测试或公共夹具：model-probe.spec.ts。约 15 行。
  - 本地依赖：`../../tests/platform/agent-test-fixture`。
- `e2e/platform/order-assignment.spec.ts` — 浏览器端到端测试或公共夹具：order-assignment.spec.ts。约 112 行。
  - 本地依赖：`./console-fixture`、`../../tests/platform/agent-test-fixture`。
- `e2e/platform/page-increments.spec.ts` — 浏览器端到端测试或公共夹具：page-increments.spec.ts。约 246 行。
  - 本地依赖：`../../src/views/home/demo/mockData.js`、`./console-fixture`。
  - 公开方法/导出标识：`setup`、`business`。
- `e2e/platform/scene-interactions.spec.ts` — 浏览器端到端测试或公共夹具：scene-interactions.spec.ts。约 85 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/scene-zone-labels.spec.ts` — 浏览器端到端测试或公共夹具：scene-zone-labels.spec.ts。约 58 行。
  - 本地依赖：`./console-fixture`。
- `e2e/platform/stage1.spec.ts` — 浏览器端到端测试或公共夹具：stage1.spec.ts。约 154 行。
  - 公开方法/导出标识：`overview`、`selectCabinet`。
- `e2e/platform/weather-fallback.spec.ts` — 浏览器端到端测试或公共夹具：weather-fallback.spec.ts。约 24 行。
  - 本地依赖：`./console-fixture`。
### .

- `env.d.ts` — 前端环境及模块类型声明。约 2 行。
- `index.html` — 浏览器 HTML 入口。约 15 行。
- `package-lock.json` — npm 依赖解析锁定文件。约 5030 行。
- `package.json` — 前端依赖、运行环境要求与 npm 命令。约 50 行。
- `playwright.config.ts` — Playwright 测试配置。约 19 行。
### public

- `public/favicon.ico` — 静态图片、图标或演示视频资源。270398 字节。
### public/videos

- `public/videos/monitor-demo.mp4` — 静态图片、图标或演示视频资源。288207 字节。
### .

- `README.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 81 行。
### scripts

- `scripts/audit-unused.mjs` — 独立维护、校验、数据准备或审计工具：audit-unused.mjs。约 192 行。
  - 公开方法/导出标识：`walk`、`resolve`、`add`、`visit`、`reach`。
- `scripts/check-a13-api.mjs` — 独立维护、校验、数据准备或审计工具：check-a13-api.mjs。约 74 行。
  - 公开方法/导出标识：`get`。
- `scripts/check-console-preview.mjs` — 独立维护、校验、数据准备或审计工具：check-console-preview.mjs。约 49 行。
- `scripts/check-inspection-assistant.mjs` — 独立维护、校验、数据准备或审计工具：check-inspection-assistant.mjs。约 13 行。
- `scripts/check-stage1-api.mjs` — 独立维护、校验、数据准备或审计工具：check-stage1-api.mjs。约 62 行。
  - 公开方法/导出标识：`get`。
### src/api

- `src/api/contracts.ts` — API 通用响应契约。约 11 行。
  - 公开方法/导出标识：`ApiResponse`、`HttpMethod`。
### src/api/platform

- `src/api/platform/client.ts` — 平台 API 客户端适配。约 32 行。
  - 本地依赖：`@/services/console`。
  - 公开方法/导出标识：`mutateInventory`、`saveCabinetControl`、`getInventory`。
- `src/api/platform/demo-monitoring.ts` — 演示监测 API。约 137 行。
  - 本地依赖：`./client`、`./integrations`、`./inventory`、`@/services/console`、`@/stores/monitoring`。
  - 公开方法/导出标识：`DEMO_RULE`、`DEMO_SOURCE`、`TemperatureRequest`、`TemperatureReceipt`、`DemoOrder`、`DemoState`、`demoMonitoringApi`、`demoReceipts`。
- `src/api/platform/integrations.ts` — 集成 API。约 50 行。
  - 本地依赖：`./client`。
  - 公开方法/导出标识：`IntegrationCapabilities`、`VideoSource`、`DeviceCommand`、`InventoryKind`、`integrationApi`。
- `src/api/platform/inventory.ts` — 台账 API。约 19 行。
  - 本地依赖：`./client`、`@/types/platform`。
  - 公开方法/导出标识：`inventoryApi`。
### src

- `src/App.vue` — Vue 根组件。约 7 行。
### src/assets/business-menu

- `src/assets/business-menu/business-menu-bg.png` — 静态图片、图标或演示视频资源。8805 字节。
- `src/assets/business-menu/business-menu-item-bg-active.png` — 静态图片、图标或演示视频资源。14280 字节。
- `src/assets/business-menu/business-menu-item-bg.png` — 静态图片、图标或演示视频资源。15399 字节。
- `src/assets/business-menu/operations.png` — 静态图片、图标或演示视频资源。6275 字节。
- `src/assets/business-menu/park.png` — 静态图片、图标或演示视频资源。4864 字节。
- `src/assets/business-menu/parking.png` — 静态图片、图标或演示视频资源。5308 字节。
- `src/assets/business-menu/security.png` — 静态图片、图标或演示视频资源。3589 字节。
- `src/assets/business-menu/visitor.png` — 静态图片、图标或演示视频资源。3969 字节。
### src/assets/css

- `src/assets/css/element.scss` — 全局或业务局部样式。约 179 行。
- `src/assets/css/main.scss` — 全局或业务局部样式。约 10 行。
### src/assets/icon

- `src/assets/icon/back.png` — 静态图片、图标或演示视频资源。747756 字节。
- `src/assets/icon/global-perspective.png` — 静态图片、图标或演示视频资源。10437 字节。
### src/assets/layouts

- `src/assets/layouts/layout-bg.png` — 静态图片、图标或演示视频资源。2617612 字节。
- `src/assets/layouts/login-bg.png` — 静态图片、图标或演示视频资源。1128204 字节。
- `src/assets/layouts/menu-btn-bg-active.png` — 静态图片、图标或演示视频资源。702 字节。
- `src/assets/layouts/menu-btn-bg.png` — 静态图片、图标或演示视频资源。434 字节。
### src/assets/monitoring

- `src/assets/monitoring/视频画面.png` — 静态图片、图标或演示视频资源。107442 字节。
### src/components/Border3/assets

- `src/components/Border3/assets/icon_right.png` — 静态图片、图标或演示视频资源。1490 字节。
- `src/components/Border3/assets/itembg.png` — 静态图片、图标或演示视频资源。216886 字节。
### src/components/Border3

- `src/components/Border3/index.vue` — 布局或可复用 UI 组件：components/Border3/index.vue。约 143 行。
  - 本地依赖：`./sequence/index.vue`。
### src/components/Border3/sequence/bg

- `src/components/Border3/sequence/bg/0.png` — 序列帧动画资源（单帧）。41262 字节。
- `src/components/Border3/sequence/bg/1.png` — 序列帧动画资源（单帧）。41317 字节。
- `src/components/Border3/sequence/bg/10.png` — 序列帧动画资源（单帧）。41528 字节。
- `src/components/Border3/sequence/bg/100.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/101.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/102.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/103.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/104.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/105.png` — 序列帧动画资源（单帧）。41216 字节。
- `src/components/Border3/sequence/bg/11.png` — 序列帧动画资源（单帧）。41516 字节。
- `src/components/Border3/sequence/bg/12.png` — 序列帧动画资源（单帧）。41500 字节。
- `src/components/Border3/sequence/bg/13.png` — 序列帧动画资源（单帧）。41572 字节。
- `src/components/Border3/sequence/bg/14.png` — 序列帧动画资源（单帧）。41605 字节。
- `src/components/Border3/sequence/bg/15.png` — 序列帧动画资源（单帧）。41575 字节。
- `src/components/Border3/sequence/bg/16.png` — 序列帧动画资源（单帧）。41638 字节。
- `src/components/Border3/sequence/bg/17.png` — 序列帧动画资源（单帧）。41656 字节。
- `src/components/Border3/sequence/bg/18.png` — 序列帧动画资源（单帧）。41710 字节。
- `src/components/Border3/sequence/bg/19.png` — 序列帧动画资源（单帧）。41742 字节。
- `src/components/Border3/sequence/bg/2.png` — 序列帧动画资源（单帧）。41301 字节。
- `src/components/Border3/sequence/bg/20.png` — 序列帧动画资源（单帧）。41746 字节。
- `src/components/Border3/sequence/bg/21.png` — 序列帧动画资源（单帧）。41822 字节。
- `src/components/Border3/sequence/bg/22.png` — 序列帧动画资源（单帧）。41893 字节。
- `src/components/Border3/sequence/bg/23.png` — 序列帧动画资源（单帧）。41923 字节。
- `src/components/Border3/sequence/bg/24.png` — 序列帧动画资源（单帧）。41961 字节。
- `src/components/Border3/sequence/bg/25.png` — 序列帧动画资源（单帧）。41950 字节。
- `src/components/Border3/sequence/bg/26.png` — 序列帧动画资源（单帧）。41953 字节。
- `src/components/Border3/sequence/bg/27.png` — 序列帧动画资源（单帧）。41946 字节。
- `src/components/Border3/sequence/bg/28.png` — 序列帧动画资源（单帧）。42002 字节。
- `src/components/Border3/sequence/bg/29.png` — 序列帧动画资源（单帧）。42005 字节。
- `src/components/Border3/sequence/bg/3.png` — 序列帧动画资源（单帧）。41318 字节。
- `src/components/Border3/sequence/bg/30.png` — 序列帧动画资源（单帧）。41994 字节。
- `src/components/Border3/sequence/bg/31.png` — 序列帧动画资源（单帧）。42028 字节。
- `src/components/Border3/sequence/bg/32.png` — 序列帧动画资源（单帧）。42036 字节。
- `src/components/Border3/sequence/bg/33.png` — 序列帧动画资源（单帧）。42082 字节。
- `src/components/Border3/sequence/bg/34.png` — 序列帧动画资源（单帧）。42082 字节。
- `src/components/Border3/sequence/bg/35.png` — 序列帧动画资源（单帧）。42109 字节。
- `src/components/Border3/sequence/bg/36.png` — 序列帧动画资源（单帧）。42132 字节。
- `src/components/Border3/sequence/bg/37.png` — 序列帧动画资源（单帧）。42118 字节。
- `src/components/Border3/sequence/bg/38.png` — 序列帧动画资源（单帧）。42122 字节。
- `src/components/Border3/sequence/bg/39.png` — 序列帧动画资源（单帧）。42133 字节。
- `src/components/Border3/sequence/bg/4.png` — 序列帧动画资源（单帧）。41343 字节。
- `src/components/Border3/sequence/bg/40.png` — 序列帧动画资源（单帧）。42166 字节。
- `src/components/Border3/sequence/bg/41.png` — 序列帧动画资源（单帧）。42158 字节。
- `src/components/Border3/sequence/bg/42.png` — 序列帧动画资源（单帧）。42128 字节。
- `src/components/Border3/sequence/bg/43.png` — 序列帧动画资源（单帧）。42145 字节。
- `src/components/Border3/sequence/bg/44.png` — 序列帧动画资源（单帧）。42135 字节。
- `src/components/Border3/sequence/bg/45.png` — 序列帧动画资源（单帧）。42121 字节。
- `src/components/Border3/sequence/bg/46.png` — 序列帧动画资源（单帧）。42112 字节。
- `src/components/Border3/sequence/bg/47.png` — 序列帧动画资源（单帧）。42127 字节。
- `src/components/Border3/sequence/bg/48.png` — 序列帧动画资源（单帧）。42100 字节。
- `src/components/Border3/sequence/bg/49.png` — 序列帧动画资源（单帧）。42072 字节。
- `src/components/Border3/sequence/bg/5.png` — 序列帧动画资源（单帧）。41348 字节。
- `src/components/Border3/sequence/bg/50.png` — 序列帧动画资源（单帧）。42133 字节。
- `src/components/Border3/sequence/bg/51.png` — 序列帧动画资源（单帧）。42131 字节。
- `src/components/Border3/sequence/bg/52.png` — 序列帧动画资源（单帧）。42104 字节。
- `src/components/Border3/sequence/bg/53.png` — 序列帧动画资源（单帧）。42109 字节。
- `src/components/Border3/sequence/bg/54.png` — 序列帧动画资源（单帧）。42040 字节。
- `src/components/Border3/sequence/bg/55.png` — 序列帧动画资源（单帧）。42082 字节。
- `src/components/Border3/sequence/bg/56.png` — 序列帧动画资源（单帧）。42064 字节。
- `src/components/Border3/sequence/bg/57.png` — 序列帧动画资源（单帧）。42078 字节。
- `src/components/Border3/sequence/bg/58.png` — 序列帧动画资源（单帧）。42094 字节。
- `src/components/Border3/sequence/bg/59.png` — 序列帧动画资源（单帧）。42065 字节。
- `src/components/Border3/sequence/bg/6.png` — 序列帧动画资源（单帧）。41395 字节。
- `src/components/Border3/sequence/bg/60.png` — 序列帧动画资源（单帧）。42052 字节。
- `src/components/Border3/sequence/bg/61.png` — 序列帧动画资源（单帧）。42060 字节。
- `src/components/Border3/sequence/bg/62.png` — 序列帧动画资源（单帧）。42068 字节。
- `src/components/Border3/sequence/bg/63.png` — 序列帧动画资源（单帧）。42070 字节。
- `src/components/Border3/sequence/bg/64.png` — 序列帧动画资源（单帧）。42029 字节。
- `src/components/Border3/sequence/bg/65.png` — 序列帧动画资源（单帧）。41944 字节。
- `src/components/Border3/sequence/bg/66.png` — 序列帧动画资源（单帧）。42025 字节。
- `src/components/Border3/sequence/bg/67.png` — 序列帧动画资源（单帧）。41944 字节。
- `src/components/Border3/sequence/bg/68.png` — 序列帧动画资源（单帧）。42010 字节。
- `src/components/Border3/sequence/bg/69.png` — 序列帧动画资源（单帧）。41968 字节。
- `src/components/Border3/sequence/bg/7.png` — 序列帧动画资源（单帧）。41408 字节。
- `src/components/Border3/sequence/bg/70.png` — 序列帧动画资源（单帧）。41932 字节。
- `src/components/Border3/sequence/bg/71.png` — 序列帧动画资源（单帧）。41916 字节。
- `src/components/Border3/sequence/bg/72.png` — 序列帧动画资源（单帧）。41922 字节。
- `src/components/Border3/sequence/bg/73.png` — 序列帧动画资源（单帧）。41889 字节。
- `src/components/Border3/sequence/bg/74.png` — 序列帧动画资源（单帧）。41806 字节。
- `src/components/Border3/sequence/bg/75.png` — 序列帧动画资源（单帧）。41827 字节。
- `src/components/Border3/sequence/bg/76.png` — 序列帧动画资源（单帧）。41802 字节。
- `src/components/Border3/sequence/bg/77.png` — 序列帧动画资源（单帧）。41691 字节。
- `src/components/Border3/sequence/bg/78.png` — 序列帧动画资源（单帧）。41717 字节。
- `src/components/Border3/sequence/bg/79.png` — 序列帧动画资源（单帧）。41667 字节。
- `src/components/Border3/sequence/bg/8.png` — 序列帧动画资源（单帧）。41457 字节。
- `src/components/Border3/sequence/bg/80.png` — 序列帧动画资源（单帧）。41605 字节。
- `src/components/Border3/sequence/bg/81.png` — 序列帧动画资源（单帧）。41551 字节。
- `src/components/Border3/sequence/bg/82.png` — 序列帧动画资源（单帧）。41524 字节。
- `src/components/Border3/sequence/bg/83.png` — 序列帧动画资源（单帧）。41511 字节。
- `src/components/Border3/sequence/bg/84.png` — 序列帧动画资源（单帧）。41479 字节。
- `src/components/Border3/sequence/bg/85.png` — 序列帧动画资源（单帧）。41401 字节。
- `src/components/Border3/sequence/bg/86.png` — 序列帧动画资源（单帧）。41384 字节。
- `src/components/Border3/sequence/bg/87.png` — 序列帧动画资源（单帧）。41483 字节。
- `src/components/Border3/sequence/bg/88.png` — 序列帧动画资源（单帧）。41338 字节。
- `src/components/Border3/sequence/bg/89.png` — 序列帧动画资源（单帧）。41385 字节。
- `src/components/Border3/sequence/bg/9.png` — 序列帧动画资源（单帧）。41502 字节。
- `src/components/Border3/sequence/bg/90.png` — 序列帧动画资源（单帧）。41409 字节。
- `src/components/Border3/sequence/bg/91.png` — 序列帧动画资源（单帧）。41355 字节。
- `src/components/Border3/sequence/bg/92.png` — 序列帧动画资源（单帧）。41323 字节。
- `src/components/Border3/sequence/bg/93.png` — 序列帧动画资源（单帧）。41303 字节。
- `src/components/Border3/sequence/bg/94.png` — 序列帧动画资源（单帧）。41241 字节。
- `src/components/Border3/sequence/bg/95.png` — 序列帧动画资源（单帧）。41234 字节。
- `src/components/Border3/sequence/bg/96.png` — 序列帧动画资源（单帧）。41226 字节。
- `src/components/Border3/sequence/bg/97.png` — 序列帧动画资源（单帧）。41200 字节。
- `src/components/Border3/sequence/bg/98.png` — 序列帧动画资源（单帧）。41190 字节。
- `src/components/Border3/sequence/bg/99.png` — 序列帧动画资源（单帧）。41217 字节。
### src/components/Border3/sequence

- `src/components/Border3/sequence/index.vue` — 布局或可复用 UI 组件：components/Border3/sequence/index.vue。约 90 行。
  - 公开方法/导出标识：`loadFrames`。
### src/components/BusinessMenu

- `src/components/BusinessMenu/index.vue` — 布局或可复用 UI 组件：components/BusinessMenu/index.vue。约 131 行。
  - 本地依赖：`./menu`。
  - 公开方法/导出标识：`selectItem`。
- `src/components/BusinessMenu/menu.ts` — 业务菜单项目与切换配置。约 20 行。
  - 本地依赖：`@/assets/business-menu/operations.png`、`@/assets/business-menu/security.png`、`@/assets/business-menu/visitor.png`、`@/assets/business-menu/parking.png`、`@/assets/business-menu/park.png`。
  - 公开方法/导出标识：`BusinessMenuItem`、`defaultBusinessMenuItems`。
- `src/components/BusinessMenu/README.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 15 行。
### src/components

- `src/components/console-dialog-theme.css` — 全局或业务局部样式。约 61 行。
- `src/components/ConsoleNotices.vue` — 布局或可复用 UI 组件：components/ConsoleNotices.vue。约 132 行。
  - 本地依赖：`./console-dialog-theme.css`、`@/services/console`。
  - 公开方法/导出标识：`load`、`markRead`、`deleteRead`、`openOrder`。
- `src/components/ConsoleSettings.vue` — 布局或可复用 UI 组件：components/ConsoleSettings.vue。约 828 行。
  - 本地依赖：`./console-dialog-theme.css`、`@/components/work-orders/WorkOrderCenter.vue`、`@/components/ConsoleNotices.vue`、`@/components/DemoMonitoringPanel.vue`、`@/services/console`。
  - 公开方法/导出标识：`loadNotices`、`showPeople`、`loadPeople`、`edit`、`save`、`remove`、`changePassword`。
- `src/components/DemoMonitoringPanel.vue` — 布局或可复用 UI 组件：components/DemoMonitoringPanel.vue。约 513 行。
  - 本地依赖：`@/services/console`、`@/stores/monitoring`、`@/api/platform/demo-monitoring`。
  - 公开方法/导出标识：`normal`、`formatTime`、`refresh`、`discardRetry`、`submit`。
### src/components/work-orders

- `src/components/work-orders/WorkOrderCenter.vue` — 布局或可复用 UI 组件：components/work-orders/WorkOrderCenter.vue。约 944 行。
  - 本地依赖：`@/services/console`、`@/utils/order-time`、`@/stores/monitoring`、`@/services/work-orders`、`../console-dialog-theme.css`。
  - 公开方法/导出标识：`metricLabel`、`retestExplanation`、`readableStep`、`showHistory`、`resetFilters`、`createManual`、`open`、`runAction`、`claim`、`start`、`submit`、`selectQuery`。
### src/layouts/components/menu

- `src/layouts/components/menu/index.vue` — 布局或可复用 UI 组件：layouts/components/menu/index.vue。约 29 行。
  - 本地依赖：`@/stores/menu`、`./src/menu-item.vue`。
### src/layouts/components/menu/src

- `src/layouts/components/menu/src/menu-button.vue` — 布局或可复用 UI 组件：layouts/components/menu。约 55 行。
- `src/layouts/components/menu/src/menu-item.vue` — 布局或可复用 UI 组件：layouts/components/menu。约 32 行。
  - 本地依赖：`./menu-button.vue`。
### src/layouts/components/top

- `src/layouts/components/top/index.vue` — 布局或可复用 UI 组件：layouts/components/top/index.vue。约 309 行。
  - 本地依赖：`@/components/ConsoleSettings.vue`、`@/services/weather`、`@/services/console`、`./sequence/index.vue`。
  - 公开方法/导出标识：`logout`、`openRequestedOrders`、`loadWeather`、`updateTime`。
### src/layouts/components/top/sequence

- `src/layouts/components/top/sequence/index.vue` — 布局或可复用 UI 组件：layouts/components/top/sequence/index.vue。约 166 行。
  - 本地依赖：`./topbg/0.png`。
  - 公开方法/导出标识：`loadImages`、`playImages`。
### src/layouts/components/top/sequence/topbg

- `src/layouts/components/top/sequence/topbg/0.png` — 序列帧动画资源（单帧）。31201 字节。
- `src/layouts/components/top/sequence/topbg/1.png` — 序列帧动画资源（单帧）。31071 字节。
- `src/layouts/components/top/sequence/topbg/10.png` — 序列帧动画资源（单帧）。33153 字节。
- `src/layouts/components/top/sequence/topbg/11.png` — 序列帧动画资源（单帧）。33626 字节。
- `src/layouts/components/top/sequence/topbg/12.png` — 序列帧动画资源（单帧）。33250 字节。
- `src/layouts/components/top/sequence/topbg/13.png` — 序列帧动画资源（单帧）。33615 字节。
- `src/layouts/components/top/sequence/topbg/14.png` — 序列帧动画资源（单帧）。33713 字节。
- `src/layouts/components/top/sequence/topbg/15.png` — 序列帧动画资源（单帧）。33270 字节。
- `src/layouts/components/top/sequence/topbg/16.png` — 序列帧动画资源（单帧）。33904 字节。
- `src/layouts/components/top/sequence/topbg/17.png` — 序列帧动画资源（单帧）。33062 字节。
- `src/layouts/components/top/sequence/topbg/18.png` — 序列帧动画资源（单帧）。34193 字节。
- `src/layouts/components/top/sequence/topbg/19.png` — 序列帧动画资源（单帧）。34525 字节。
- `src/layouts/components/top/sequence/topbg/2.png` — 序列帧动画资源（单帧）。31674 字节。
- `src/layouts/components/top/sequence/topbg/20.png` — 序列帧动画资源（单帧）。35094 字节。
- `src/layouts/components/top/sequence/topbg/21.png` — 序列帧动画资源（单帧）。35565 字节。
- `src/layouts/components/top/sequence/topbg/22.png` — 序列帧动画资源（单帧）。35384 字节。
- `src/layouts/components/top/sequence/topbg/23.png` — 序列帧动画资源（单帧）。36418 字节。
- `src/layouts/components/top/sequence/topbg/24.png` — 序列帧动画资源（单帧）。37231 字节。
- `src/layouts/components/top/sequence/topbg/25.png` — 序列帧动画资源（单帧）。36914 字节。
- `src/layouts/components/top/sequence/topbg/26.png` — 序列帧动画资源（单帧）。37535 字节。
- `src/layouts/components/top/sequence/topbg/27.png` — 序列帧动画资源（单帧）。37222 字节。
- `src/layouts/components/top/sequence/topbg/28.png` — 序列帧动画资源（单帧）。37806 字节。
- `src/layouts/components/top/sequence/topbg/29.png` — 序列帧动画资源（单帧）。36744 字节。
- `src/layouts/components/top/sequence/topbg/3.png` — 序列帧动画资源（单帧）。31495 字节。
- `src/layouts/components/top/sequence/topbg/30.png` — 序列帧动画资源（单帧）。38627 字节。
- `src/layouts/components/top/sequence/topbg/31.png` — 序列帧动画资源（单帧）。39077 字节。
- `src/layouts/components/top/sequence/topbg/32.png` — 序列帧动画资源（单帧）。38652 字节。
- `src/layouts/components/top/sequence/topbg/33.png` — 序列帧动画资源（单帧）。38828 字节。
- `src/layouts/components/top/sequence/topbg/34.png` — 序列帧动画资源（单帧）。39954 字节。
- `src/layouts/components/top/sequence/topbg/35.png` — 序列帧动画资源（单帧）。39051 字节。
- `src/layouts/components/top/sequence/topbg/36.png` — 序列帧动画资源（单帧）。39716 字节。
- `src/layouts/components/top/sequence/topbg/37.png` — 序列帧动画资源（单帧）。40865 字节。
- `src/layouts/components/top/sequence/topbg/38.png` — 序列帧动画资源（单帧）。39297 字节。
- `src/layouts/components/top/sequence/topbg/39.png` — 序列帧动画资源（单帧）。39517 字节。
- `src/layouts/components/top/sequence/topbg/4.png` — 序列帧动画资源（单帧）。31275 字节。
- `src/layouts/components/top/sequence/topbg/40.png` — 序列帧动画资源（单帧）。41636 字节。
- `src/layouts/components/top/sequence/topbg/41.png` — 序列帧动画资源（单帧）。41471 字节。
- `src/layouts/components/top/sequence/topbg/42.png` — 序列帧动画资源（单帧）。41046 字节。
- `src/layouts/components/top/sequence/topbg/43.png` — 序列帧动画资源（单帧）。41602 字节。
- `src/layouts/components/top/sequence/topbg/44.png` — 序列帧动画资源（单帧）。41087 字节。
- `src/layouts/components/top/sequence/topbg/45.png` — 序列帧动画资源（单帧）。41744 字节。
- `src/layouts/components/top/sequence/topbg/46.png` — 序列帧动画资源（单帧）。41480 字节。
- `src/layouts/components/top/sequence/topbg/47.png` — 序列帧动画资源（单帧）。40953 字节。
- `src/layouts/components/top/sequence/topbg/48.png` — 序列帧动画资源（单帧）。42815 字节。
- `src/layouts/components/top/sequence/topbg/49.png` — 序列帧动画资源（单帧）。41966 字节。
- `src/layouts/components/top/sequence/topbg/5.png` — 序列帧动画资源（单帧）。32227 字节。
- `src/layouts/components/top/sequence/topbg/50.png` — 序列帧动画资源（单帧）。41633 字节。
- `src/layouts/components/top/sequence/topbg/51.png` — 序列帧动画资源（单帧）。40590 字节。
- `src/layouts/components/top/sequence/topbg/52.png` — 序列帧动画资源（单帧）。40144 字节。
- `src/layouts/components/top/sequence/topbg/53.png` — 序列帧动画资源（单帧）。40815 字节。
- `src/layouts/components/top/sequence/topbg/54.png` — 序列帧动画资源（单帧）。39609 字节。
- `src/layouts/components/top/sequence/topbg/55.png` — 序列帧动画资源（单帧）。40108 字节。
- `src/layouts/components/top/sequence/topbg/56.png` — 序列帧动画资源（单帧）。39100 字节。
- `src/layouts/components/top/sequence/topbg/57.png` — 序列帧动画资源（单帧）。38762 字节。
- `src/layouts/components/top/sequence/topbg/58.png` — 序列帧动画资源（单帧）。37545 字节。
- `src/layouts/components/top/sequence/topbg/59.png` — 序列帧动画资源（单帧）。36983 字节。
- `src/layouts/components/top/sequence/topbg/6.png` — 序列帧动画资源（单帧）。31534 字节。
- `src/layouts/components/top/sequence/topbg/60.png` — 序列帧动画资源（单帧）。37150 字节。
- `src/layouts/components/top/sequence/topbg/61.png` — 序列帧动画资源（单帧）。38114 字节。
- `src/layouts/components/top/sequence/topbg/62.png` — 序列帧动画资源（单帧）。37240 字节。
- `src/layouts/components/top/sequence/topbg/63.png` — 序列帧动画资源（单帧）。37692 字节。
- `src/layouts/components/top/sequence/topbg/64.png` — 序列帧动画资源（单帧）。37266 字节。
- `src/layouts/components/top/sequence/topbg/65.png` — 序列帧动画资源（单帧）。37197 字节。
- `src/layouts/components/top/sequence/topbg/66.png` — 序列帧动画资源（单帧）。36305 字节。
- `src/layouts/components/top/sequence/topbg/67.png` — 序列帧动画资源（单帧）。36189 字节。
- `src/layouts/components/top/sequence/topbg/68.png` — 序列帧动画资源（单帧）。35490 字节。
- `src/layouts/components/top/sequence/topbg/69.png` — 序列帧动画资源（单帧）。34074 字节。
- `src/layouts/components/top/sequence/topbg/7.png` — 序列帧动画资源（单帧）。32308 字节。
- `src/layouts/components/top/sequence/topbg/70.png` — 序列帧动画资源（单帧）。33818 字节。
- `src/layouts/components/top/sequence/topbg/71.png` — 序列帧动画资源（单帧）。32779 字节。
- `src/layouts/components/top/sequence/topbg/72.png` — 序列帧动画资源（单帧）。32111 字节。
- `src/layouts/components/top/sequence/topbg/73.png` — 序列帧动画资源（单帧）。31636 字节。
- `src/layouts/components/top/sequence/topbg/74.png` — 序列帧动画资源（单帧）。31201 字节。
- `src/layouts/components/top/sequence/topbg/8.png` — 序列帧动画资源（单帧）。32609 字节。
- `src/layouts/components/top/sequence/topbg/9.png` — 序列帧动画资源（单帧）。33426 字节。
### src/layouts

- `src/layouts/index.vue` — 布局或可复用 UI 组件：layouts/index.vue。约 126 行。
  - 本地依赖：`./components/top/index.vue`、`./components/menu/index.vue`、`@/components/BusinessMenu/index.vue`、`@/components/BusinessMenu/menu`。
  - 公开方法/导出标识：`setBusiness`、`selectBusiness`。
### src

- `src/main.ts` — Vue 应用启动，注册 Pinia、路由、Element Plus 与全局样式。约 24 行。
  - 本地依赖：`./assets/css/main.scss`、`./assets/css/element.scss`、`./App.vue`、`./router`。
### src/router

- `src/router/index.ts` — 路由定义、登录守卫与角色跳转。约 62 行。
  - 本地依赖：`@/layouts/index.vue`、`@/services/console`、`@/views/home/index.vue`、`@/views/user/index.vue`、`@/views/maintenance/index.vue`、`@/views/login/index.vue`。
### src/services

- `src/services/console.ts` — 认证会话、fetch 请求封装及控制台 API。约 78 行。
  - 本地依赖：`@/utils/unified-data-source`、`@/api/contracts`。
  - 公开方法/导出标识：`ConsoleUser`、`auth`、`clearSession`、`consoleApi`、`signIn`、`checkSession`、`signOut`。
- `src/services/weather.ts` — 天气服务调用。约 32 行。
  - 本地依赖：`./console`。
  - 公开方法/导出标识：`getWeather`。
- `src/services/work-orders.ts` — 工单前端服务与共享状态。约 122 行。
  - 本地依赖：`@/services/console`。
  - 公开方法/导出标识：`FrontendOrder`、`frontendOrders`、`orderStorage`、`merge`、`loadOrders`、`createOrder`、`orderAction`、`submitFrontendOrder`。
### src/stores

- `src/stores/menu.ts` — 业务菜单项目与切换配置。约 10 行。
  - 公开方法/导出标识：`useMenuStore`。
- `src/stores/monitoring.ts` — 监测、告警与能耗 Pinia 状态。约 258 行。
  - 本地依赖：`@/api/platform/client`、`@/utils/alarm-levels`。
  - 公开方法/导出标识：`MonitorReading`、`MonitorRecord`、`CabinetControlRecord`、`LinkQualityPoint`、`MonitoringSnapshot`、`EnergySnapshot`、`useMonitoringStore`、`load`、`loadEnergy`、`refresh`、`loadWeek`、`reading`、`cabinetAlarms`、`cabinetAlarm`、`cabinetStatus`、`clear`。
- `src/stores/platform.ts` — 台账、场景、机柜和身份索引 Pinia 状态。约 139 行。
  - 本地依赖：`@/api/platform/inventory`、`@/types/platform`、`@/utils/platform-identity`。
  - 公开方法/导出标识：`usePlatformStore`、`remember`、`clearSelection`、`selectCabinet`、`load`。
### src/types

- `src/types/legacy-vue.d.ts` — TypeScript 类型或第三方模块声明。约 8 行。
- `src/types/platform-identity.ts` — 台账/场景身份类型或解析工具，按所在目录区分。约 36 行。
  - 公开方法/导出标识：`DeviceRef`、`CabinetRef`、`InventoryRef`、`UnlinkedReason`、`IdentityResolution`、`PlatformIdentityIndex`。
- `src/types/platform.ts` — TypeScript 类型或第三方模块声明。约 91 行。
  - 公开方法/导出标识：`DataMeta`、`Room`、`Zone`、`Reading`、`Cabinet`、`Device`、`Page`、`SceneSnapshot`。
### src/utils

- `src/utils/alarm-levels.ts` — 告警级别转换和显示工具。约 15 行。
  - 公开方法/导出标识：`alarmLevels`、`alarmLevelName`、`alarmLevelRank`。
- `src/utils/order-time.ts` — 工单时间处理工具。约 25 行。
  - 公开方法/导出标识：`formatOrderTime`、`formatOrderStep`。
- `src/utils/platform-display.ts` — 台账展示格式与文案工具。约 19 行。
  - 本地依赖：`@/types/platform`。
  - 公开方法/导出标识：`originLabel`、`runtimeLabel`、`readingLabel`、`uPositionLabel`、`registrationLabel`。
- `src/utils/platform-identity.ts` — 台账/场景身份类型或解析工具，按所在目录区分。约 157 行。
  - 本地依赖：`@/types/platform`、`@/types/platform-identity`。
  - 公开方法/导出标识：`buckets`、`unlinked`、`linked`、`buildPlatformIdentityIndex`、`cabinetIssue`、`sameOrigin`、`deviceIssue`、`resolveCabinet`、`resolveDevice`。
- `src/utils/unified-data-source.ts` — 统一数据来源前端规则。约 19 行。
  - 公开方法/导出标识：`normalizeDataSource`。
### src/views/home/assistant

- `src/views/home/assistant/agent-assistant.ts` — 助手交互协调。约 28 行。
  - 本地依赖：`@/types/platform-identity`、`./agent-client`、`./agent-conversation`、`./home-scene-controls`、`./scene-action-executor`。
  - 公开方法/导出标识：`createAgentAssistant`、`select`、`AgentAssistant`。
- `src/views/home/assistant/agent-client.ts` — 助手 HTTP/SSE 传输。约 101 行。
  - 本地依赖：`@/services/console`、`./scene-action-executor`。
  - 公开方法/导出标识：`AgentSession`、`AgentStreamEvent`、`agentRequest`。
- `src/views/home/assistant/agent-conversation.ts` — 助手对话状态和消息处理。约 112 行。
  - 本地依赖：`./agent-client`、`./scene-action-executor`。
  - 公开方法/导出标识：`AgentConversationState`、`createAgentConversation`、`apply`、`enable`、`command`、`checkConfirmation`、`confirm`、`dismiss`。
- `src/views/home/assistant/AssistantMarkdown.vue` — 助手 Markdown 消息渲染。约 112 行。
- `src/views/home/assistant/home-scene-controls.ts` — 首页可供助手调用的场景控制接口。约 16 行。
  - 本地依赖：`./scene-action-executor`。
  - 公开方法/导出标识：`createHomeSceneControls`、`select`。
- `src/views/home/assistant/InspectionAssistant.vue` — 巡检助手交互界面。约 527 行。
  - 本地依赖：`@/types/platform-identity`、`./voice-turn.js`、`./agent-assistant`、`./AssistantMarkdown.vue`。
  - 公开方法/导出标识：`inspectContext`、`send`、`submit`、`stopVoice`、`toggleVoice`。
- `src/views/home/assistant/scene-action-executor.ts` — 执行助手发出的浏览器场景动作。约 106 行。
  - 公开方法/导出标识：`SceneActionType`、`SceneAction`、`SceneReceipt`、`AgentScene`、`createSceneActionExecutor`、`execute`。
- `src/views/home/assistant/voice-turn.d.ts` — 语音轮次模块类型声明。约 11 行。
  - 公开方法/导出标识：`normalizeVoiceText`、`assessSpeechAlternatives`。
- `src/views/home/assistant/voice-turn.js` — 语音轮次处理。约 57 行。
  - 公开方法/导出标识：`normalizeVoiceText`、`cabinetSceneModelKeyFromSpeech`、`assessSpeechAlternatives`。
### src/views/home/cockpit/alarms/components

- `src/views/home/cockpit/alarms/components/ActiveAlarms.vue` — 页面或业务展示组件：views/home/cockpit/alarms/components/ActiveAlarms.vue。约 279 行。
  - 本地依赖：`@/utils/alarm-levels`、`@/stores/monitoring`、`@/stores/platform`、`../../../../../services/work-orders`。
  - 公开方法/导出标识：`orderFor`、`formatTime`、`locate`、`create`。
- `src/views/home/cockpit/alarms/components/AlarmAnalysisCard.vue` — 页面或业务展示组件：views/home/cockpit/alarms/components/AlarmAnalysisCard.vue。约 446 行。
  - 本地依赖：`@/stores/monitoring`、`./ActiveAlarms.vue`、`@/utils/alarm-levels`。
  - 公开方法/导出标识：`renderChart`。
- `src/views/home/cockpit/alarms/components/AlarmOverviewCard.vue` — 页面或业务展示组件：views/home/cockpit/alarms/components/AlarmOverviewCard.vue。约 220 行。
  - 本地依赖：`@/stores/monitoring`、`@/utils/alarm-levels`、`../../../../../services/work-orders`。
- `src/views/home/cockpit/alarms/components/index.ts` — 同目录模块集中导出入口。约 4 行。
  - 本地依赖：`./AlarmOverviewCard.vue`、`./AlarmAnalysisCard.vue`、`./InspectionAssistantCard.vue`。
- `src/views/home/cockpit/alarms/components/InspectionAssistantCard.vue` — 页面或业务展示组件：views/home/cockpit/alarms/components/InspectionAssistantCard.vue。约 12 行。
  - 本地依赖：`../../../assistant/InspectionAssistant.vue`、`../../../assistant/agent-assistant`、`@/types/platform-identity`。
### src/views/home/cockpit/alarms

- `src/views/home/cockpit/alarms/index.vue` — 页面或业务展示组件：views/home/cockpit/alarms/index.vue。约 26 行。
  - 本地依赖：`@/components/Border3/index.vue`、`../../composables/useHomeDashboard`、`./components`。
### src/views/home/cockpit/assets/components

- `src/views/home/cockpit/assets/components/AssetLocationsCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/AssetLocationsCard.vue。约 19 行。
- `src/views/home/cockpit/assets/components/AssetStatusCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/AssetStatusCard.vue。约 28 行。
- `src/views/home/cockpit/assets/components/AssetTypesCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/AssetTypesCard.vue。约 25 行。
- `src/views/home/cockpit/assets/components/CabinetCapacityCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/CabinetCapacityCard.vue。约 116 行。
- `src/views/home/cockpit/assets/components/index.ts` — 同目录模块集中导出入口。约 7 行。
  - 本地依赖：`./AssetTypesCard.vue`、`./SurveillanceCard.vue`、`./AssetStatusCard.vue`、`./SpaceUsageCard.vue`、`./AssetLocationsCard.vue`、`./CabinetCapacityCard.vue`。
- `src/views/home/cockpit/assets/components/SpaceUsageCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/SpaceUsageCard.vue。约 31 行。
- `src/views/home/cockpit/assets/components/SurveillanceCard.vue` — 页面或业务展示组件：views/home/cockpit/assets/components/SurveillanceCard.vue。约 10 行。
  - 本地依赖：`../../../components/VideoSurveillance.vue`。
### src/views/home/cockpit/assets/composables

- `src/views/home/cockpit/assets/composables/useAssetDashboard.js` — 资产驾驶舱组合逻辑。约 217 行。
  - 本地依赖：`@/stores/monitoring`、`@/stores/platform`、`@/api/platform/inventory`、`../../../scene/platform-scene-adapter`、`../../../scene/zone-monitoring`、`../../../scene/cabinet-monitoring`、`@/utils/platform-display`。
  - 公开方法/导出标识：`useAssetDashboard`、`statusPercent`、`returnToRoom`、`selectModelAsset`、`assetSelectZone`。
### src/views/home/cockpit/assets

- `src/views/home/cockpit/assets/index.vue` — 页面或业务展示组件：views/home/cockpit/assets/index.vue。约 109 行。
  - 本地依赖：`@/components/Border3/index.vue`、`./components`、`../../scene/RoomScene.vue`、`./composables/useAssetDashboard`。
### src/views/home/cockpit/assets/styles

- `src/views/home/cockpit/assets/styles/assets.css` — 全局或业务局部样式。约 928 行。
### src/views/home/cockpit/cabinet/components

- `src/views/home/cockpit/cabinet/components/CabinetAlarmsCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/CabinetAlarmsCard.vue。约 27 行。
  - 本地依赖：`../shared/OperationsCard.vue`。
- `src/views/home/cockpit/cabinet/components/CabinetControlCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/CabinetControlCard.vue。约 27 行。
  - 本地依赖：`../shared/OperationsCard.vue`。
- `src/views/home/cockpit/cabinet/components/CabinetLocationCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/CabinetLocationCard.vue。约 27 行。
  - 本地依赖：`../shared/OperationsCard.vue`。
- `src/views/home/cockpit/cabinet/components/CabinetStatusCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/CabinetStatusCard.vue。约 27 行。
  - 本地依赖：`../shared/OperationsCard.vue`。
- `src/views/home/cockpit/cabinet/components/DeviceListCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/DeviceListCard.vue。约 201 行。
  - 本地依赖：`../../../demo/topologyData`、`../../../scene/cabinetState`、`@/types/platform`、`@/utils/platform-display`、`@/stores/monitoring`。
  - 公开方法/导出标识：`deviceStatus`、`reading`、`selectDevice`。
- `src/views/home/cockpit/cabinet/components/DeviceStatus.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/DeviceStatus.vue。约 255 行。
  - 本地依赖：`../../../scene/cabinetState`、`../../../demo/topologyData`、`../../../demo/mockData.js`、`@/stores/monitoring`、`@/stores/platform`。
  - 公开方法/导出标识：`realState`、`updateChart`。
- `src/views/home/cockpit/cabinet/components/index.ts` — 同目录模块集中导出入口。约 7 行。
  - 本地依赖：`./CabinetLocationCard.vue`、`./CabinetStatusCard.vue`、`./DeviceListCard.vue`、`./CabinetAlarmsCard.vue`、`./CabinetControlCard.vue`、`./OperationLogsCard.vue`。
- `src/views/home/cockpit/cabinet/components/OperationLogsCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/components/OperationLogsCard.vue。约 27 行。
  - 本地依赖：`../shared/OperationsCard.vue`。
### src/views/home/cockpit/cabinet

- `src/views/home/cockpit/cabinet/index.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/index.vue。约 108 行。
  - 本地依赖：`@/components/Border3/index.vue`、`../../composables/useHomeDashboard`、`./components`。
### src/views/home/cockpit/cabinet/shared

- `src/views/home/cockpit/cabinet/shared/OperationsCard.vue` — 页面或业务展示组件：views/home/cockpit/cabinet/shared/OperationsCard.vue。约 608 行。
  - 本地依赖：`../components/DeviceStatus.vue`、`@/stores/monitoring`、`@/stores/platform`、`@/utils/alarm-levels`、`../../../scene/cabinetState`。
  - 公开方法/导出标识：`CabinetOperations`、`resetLogLoop`、`formatAlarmTime`、`toggle`。
### src/views/home/cockpit

- `src/views/home/cockpit/card-groups.ts` — 驾驶舱卡片分组配置。约 19 行。
  - 公开方法/导出标识：`cardGroups`。
### src/views/home/cockpit/energy/components

- `src/views/home/cockpit/energy/components/CabinetEnergyRankingCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/CabinetEnergyRankingCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
- `src/views/home/cockpit/energy/components/ElectricalAlarmsCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/ElectricalAlarmsCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
- `src/views/home/cockpit/energy/components/EnergyCategoryCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/EnergyCategoryCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
- `src/views/home/cockpit/energy/components/EnergyOverviewCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/EnergyOverviewCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
- `src/views/home/cockpit/energy/components/EnergyTrend.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/EnergyTrend.vue。约 184 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`renderChart`。
- `src/views/home/cockpit/energy/components/EnergyTrendCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/EnergyTrendCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
- `src/views/home/cockpit/energy/components/index.ts` — 同目录模块集中导出入口。约 7 行。
  - 本地依赖：`./EnergyOverviewCard.vue`、`./EnergyCategoryCard.vue`、`./ElectricalAlarmsCard.vue`、`./PueCard.vue`、`./CabinetEnergyRankingCard.vue`、`./EnergyTrendCard.vue`。
- `src/views/home/cockpit/energy/components/PueCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/components/PueCard.vue。约 8 行。
  - 本地依赖：`../shared/EnergyManagementCard.vue`。
### src/views/home/cockpit/energy

- `src/views/home/cockpit/energy/energy-alarms.ts` — 能耗相关告警处理。约 10 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`isElectricalAlarm`。
- `src/views/home/cockpit/energy/energy-data.ts` — 能耗图表数据与周期状态。约 19 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`energyPeriods`、`energyPeriod`、`energyData`。
- `src/views/home/cockpit/energy/index.vue` — 页面或业务展示组件：views/home/cockpit/energy/index.vue。约 29 行。
  - 本地依赖：`@/components/Border3/index.vue`、`../../composables/useHomeDashboard`、`./components`。
### src/views/home/cockpit/energy/shared

- `src/views/home/cockpit/energy/shared/EnergyManagementCard.vue` — 页面或业务展示组件：views/home/cockpit/energy/shared/EnergyManagementCard.vue。约 800 行。
  - 本地依赖：`../energy-data`、`../energy-alarms`、`@/stores/monitoring`。
  - 公开方法/导出标识：`formatEnergy`、`formatAlertTime`、`inPeriod`、`periodAverage`、`renderPue`、`renderMix`、`renderRanking`、`toggleTrend`、`renderChart`。
### src/views/home/cockpit/room/components

- `src/views/home/cockpit/room/components/EnvironmentCard.vue` — 页面或业务展示组件：views/home/cockpit/room/components/EnvironmentCard.vue。约 289 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`setElement`、`renderCharts`。
- `src/views/home/cockpit/room/components/FacilityListCard.vue` — 页面或业务展示组件：views/home/cockpit/room/components/FacilityListCard.vue。约 259 行。
  - 本地依赖：`@/utils/alarm-levels`。
  - 公开方法/导出标识：`selectDevice`、`statusOf`。
- `src/views/home/cockpit/room/components/index.ts` — 同目录模块集中导出入口。约 7 行。
  - 本地依赖：`./RoomInfoCard.vue`、`./FacilityListCard.vue`、`./EnvironmentCard.vue`、`./WeeklyEnergyCard.vue`、`./RoomEventsCard.vue`。
- `src/views/home/cockpit/room/components/RoomEventsCard.vue` — 页面或业务展示组件：views/home/cockpit/room/components/RoomEventsCard.vue。约 268 行。
  - 本地依赖：`@/stores/monitoring`、`@/stores/platform`、`@/utils/alarm-levels`。
  - 公开方法/导出标识：`startDescription`、`stopDescription`、`resetLoop`。
- `src/views/home/cockpit/room/components/RoomInfoCard.vue` — 页面或业务展示组件：views/home/cockpit/room/components/RoomInfoCard.vue。约 68 行。
  - 本地依赖：`@/types/platform`。
- `src/views/home/cockpit/room/components/WeeklyEnergyCard.vue` — 页面或业务展示组件：views/home/cockpit/room/components/WeeklyEnergyCard.vue。约 8 行。
  - 本地依赖：`../../energy/components/EnergyTrend.vue`。
### src/views/home/cockpit/room

- `src/views/home/cockpit/room/index.vue` — 页面或业务展示组件：views/home/cockpit/room/index.vue。约 49 行。
  - 本地依赖：`../../components/EmptyContent.vue`、`@/components/Border3/index.vue`、`../../composables/useHomeDashboard`、`./components`。
### src/views/home/cockpit/topology/components

- `src/views/home/cockpit/topology/components/ConnectionDetailsCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/ConnectionDetailsCard.vue。约 124 行。
  - 公开方法/导出标识：`formatTime`。
- `src/views/home/cockpit/topology/components/index.ts` — 同目录模块集中导出入口。约 7 行。
  - 本地依赖：`./NetworkOverviewCard.vue`、`./NetworkLinksCard.vue`、`./NetworkLayersCard.vue`、`./PortStatusCard.vue`、`./LinkQualityCard.vue`、`./ConnectionDetailsCard.vue`。
- `src/views/home/cockpit/topology/components/LinkQualityAnalysis.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/LinkQualityAnalysis.vue。约 274 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`line`。
- `src/views/home/cockpit/topology/components/LinkQualityCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/LinkQualityCard.vue。约 196 行。
  - 本地依赖：`@/stores/monitoring`、`./LinkQualityAnalysis.vue`。
- `src/views/home/cockpit/topology/components/NetworkLayersCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/NetworkLayersCard.vue。约 163 行。
- `src/views/home/cockpit/topology/components/NetworkLinksCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/NetworkLinksCard.vue。约 160 行。
- `src/views/home/cockpit/topology/components/NetworkOverview.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/NetworkOverview.vue。约 224 行。
  - 本地依赖：`@/stores/monitoring`、`@/stores/platform`。
- `src/views/home/cockpit/topology/components/NetworkOverviewCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/NetworkOverviewCard.vue。约 146 行。
  - 本地依赖：`./NetworkOverview.vue`。
- `src/views/home/cockpit/topology/components/PortStatusAnalysis.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/PortStatusAnalysis.vue。约 192 行。
- `src/views/home/cockpit/topology/components/PortStatusCard.vue` — 页面或业务展示组件：views/home/cockpit/topology/components/PortStatusCard.vue。约 152 行。
  - 本地依赖：`./PortStatusAnalysis.vue`。
### src/views/home/cockpit/topology

- `src/views/home/cockpit/topology/DrilldownTopology.vue` — 页面或业务展示组件：views/home/cockpit/topology/DrilldownTopology.vue。约 441 行。
  - 本地依赖：`@/stores/monitoring`、`./topology-layout`、`./topology-drilldown`、`./topology-search`。
  - 公开方法/导出标识：`navigate`、`openBox`、`openSearchResult`、`openFirstResult`、`boxName`、`selectLink`、`linkPath`、`layerColor`。
- `src/views/home/cockpit/topology/index.vue` — 页面或业务展示组件：views/home/cockpit/topology/index.vue。约 352 行。
  - 本地依赖：`@/components/Border3/index.vue`、`./components`、`./DrilldownTopology.vue`、`@/stores/monitoring`、`@/stores/platform`、`@/types/platform-identity`、`./topology-layout`。
  - 公开方法/导出标识：`nodeStatus`、`locate`。
- `src/views/home/cockpit/topology/topology-drilldown.ts` — 拓扑层级下钻逻辑。约 201 行。
  - 本地依赖：`@/stores/monitoring`、`./topology-layout`。
  - 公开方法/导出标识：`TopologyScope`、`TopologyBox`、`buildDrilldownTopology`、`row`。
- `src/views/home/cockpit/topology/topology-layout.ts` — 拓扑布局。约 6 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`TopologyLink`。
- `src/views/home/cockpit/topology/topology-search.ts` — 拓扑检索。约 33 行。
  - 本地依赖：`@/stores/monitoring`。
  - 公开方法/导出标识：`searchTopologyNodes`。
### src/views/home/components

- `src/views/home/components/EmptyContent.vue` — 页面或业务展示组件：views/home/components/EmptyContent.vue。约 4 行。
- `src/views/home/components/VideoSurveillance.vue` — 页面或业务展示组件：views/home/components/VideoSurveillance.vue。约 353 行。
  - 本地依赖：`@/assets/monitoring/视频画面.png`、`../scene/cameras`、`@/stores/monitoring`、`@/api/platform/integrations`。
  - 公开方法/导出标识：`loadVideo`、`retryVideo`、`navigate`。
### src/views/home/composables

- `src/views/home/composables/useCabinetControls.ts` — 机柜操作及控制请求协调。约 134 行。
  - 本地依赖：`@/stores/platform`、`@/stores/monitoring`、`@/api/platform/client`、`@/api/platform/integrations`、`../cockpit/cabinet/shared/OperationsCard.vue`、`../scene/scene.ts`、`@/types/platform`、`../scene/RoomScene.vue`、`../scene/cabinetState`。
  - 公开方法/导出标识：`useCabinetControls`、`updateControl`。
- `src/views/home/composables/useHomeDashboard.ts` — 首页驾驶舱数据加载、刷新及功能能力协调。约 289 行。
  - 本地依赖：`./useCabinetControls`、`./useHomeSceneData`、`../cockpit/card-groups`、`@/types/platform-identity`、`../demo/legacy-demo-scene-bridge`、`../assistant/agent-assistant`、`@/services/work-orders`、`../scene/facilities`、`@/api/platform/integrations`、`../scene/RoomScene.vue`。
  - 公开方法/导出标识：`HomeProps`、`HomeEmit`、`useHomeDashboard`、`selectForAssistant`、`inspectFromAssistant`、`locateAlarm`、`createAlarmOrder`、`cabinetStatus`、`showDeviceDetail`、`inspectFromScene`、`leaveDeviceDetail`、`extractInventoryDevice`、`locateTopologyDevice`、`selectFacility`、`enterCabinet`、`retryDetail`、`goBack`、`HomeDashboard`。
- `src/views/home/composables/useHomeSceneData.ts` — 台账和监测数据到场景的响应式连接。约 62 行。
  - 本地依赖：`@/stores/platform`、`@/stores/monitoring`、`../cockpit/energy/energy-data`、`../scene/platform-scene-adapter`、`../scene/scene.ts`、`../scene/zone-monitoring`、`../scene/cabinet-monitoring`。
  - 公开方法/导出标识：`useHomeSceneData`、`refreshMonitoring`。
### src/views/home/demo

- `src/views/home/demo/legacy-demo-scene-bridge.ts` — 旧演示数据与场景的桥接适配。约 39 行。
  - 公开方法/导出标识：`createLegacyDemoBridge`、`ensure`。
- `src/views/home/demo/mockData.d.ts` — TypeScript 类型或第三方模块声明。约 45 行。
  - 本地依赖：`../scene/types`。
  - 公开方法/导出标识：`mockData`。
- `src/views/home/demo/mockData.js` — 演示机房与设备数据。约 388 行。
  - 公开方法/导出标识：`mockData`。
- `src/views/home/demo/topologyData.ts` — 演示网络拓扑数据。约 58 行。
  - 公开方法/导出标识：`topologyNodes`。
### src/views/home

- `src/views/home/index.vue` — 页面或业务展示组件：views/home/index.vue。约 76 行。
  - 本地依赖：`./scene/RoomScene.vue`、`./cockpit/room/index.vue`、`./cockpit/cabinet/index.vue`、`./cockpit/energy/index.vue`、`./cockpit/alarms/index.vue`、`./cockpit/topology/index.vue`、`./cockpit/assets/index.vue`、`./composables/useHomeDashboard`。
- `src/views/home/README.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 58 行。
### src/views/home/scene

- `src/views/home/scene/cabinet-monitoring.ts` — 机柜监测与场景状态同步。约 21 行。
  - 本地依赖：`./scene.ts`、`@/types/platform`、`@/stores/monitoring`。
  - 公开方法/导出标识：`syncCabinetMonitoring`。
- `src/views/home/scene/cabinetState.ts` — 演示机柜控制和告警状态。约 41 行。
  - 本地依赖：`../demo/mockData.js`。
  - 公开方法/导出标识：`CabinetModelControls`、`cabinetControls`、`controlKeys`、`DeviceModelAlarm`、`deviceModelAlarms`。
- `src/views/home/scene/cameras.ts` — 摄像头配置。约 53 行。
  - 公开方法/导出标识：`CameraStatus`、`RoomCamera`、`cameras`。
- `src/views/home/scene/facilities.ts` — 场景设施配置。约 15 行。
  - 本地依赖：`./cameras`。
  - 公开方法/导出标识：`airConditioners`、`demoFacilities`。
- `src/views/home/scene/icons.ts` — 场景 SVG 图标。约 23 行。
  - 公开方法/导出标识：`icon`。
- `src/views/home/scene/patrol.ts` — 巡检人物建模和移动。约 278 行。
  - 本地依赖：`./vendor/three.module.js`、`../demo/mockData.js`。
  - 公开方法/导出标识：`PatrolDevice`、`PatrolResult`、`createPatrol`、`part`、`setLabel`、`publish`、`cancel`、`pause`、`toFront`、`resume`、`visit`、`arrive`、`update`。
- `src/views/home/scene/platform-scene-adapter.ts` — 后端台账到三维模型的转换及身份映射。约 82 行。
  - 本地依赖：`@/types/platform`、`../demo/topologyData`、`../../../utils/platform-identity`、`@/types/platform-identity`。
  - 公开方法/导出标识：`adaptPlatformScene`、`adaptCabinetInventory`。
- `src/views/home/scene/README.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 40 行。
- `src/views/home/scene/RoomScene.vue` — 三维场景 Vue 容器、生命周期和交互桥接。约 421 行。
  - 本地依赖：`./scene.ts`、`../demo/mockData.js`、`../demo/topologyData`、`./cabinetState`、`./facilities`。
  - 公开方法/导出标识：`putDeviceBack`、`restoreExtraction`、`extractDevice`、`select`、`inspect`、`toggleDoor`、`overview`、`resetView`、`locateFacility`、`visit`、`cancelPatrol`、`resumePatrol`、`setDeviceDoor`、`setAlarm`、`syncModelControls`、`syncMonitoring`、`patrolState`。
- `src/views/home/scene/scene.ts` — Three.js 场景建模、渲染、拾取、交互、动画和资源释放。约 1855 行。
  - 本地依赖：`./types`、`./vendor/three.module.js`、`./vendor/OrbitControls.js`、`../demo/mockData.js`、`./patrol.ts`、`./icons.ts`、`./cameras.ts`、`./facilities.ts`、`../demo/topologyData.ts`、`@/utils/alarm-levels`。
  - 公开方法/导出标识：`renderable`、`child`、`createRoom`、`boxGeometry`、`mat`、`box`、`texture`、`moduleTexture`、`instances`、`techBase`、`markTarget`、`highlight`、`openZone`、`closeDetail`、`hitTest`、`cameraFromHit`、`deviceFromHit`、`projectLabels`、`fitRoom`、`resize`、`configureCamera`、`clearFacility`、`locateFacility`、`resetView`、`cabinetSettings`、`updateDeviceVisual`、`setDeviceAlarm`、`applyInspectionControls`、`setCabinetControls`、`addCabinetDetails`、`resetExtractedDevice`、`returnDevice`、`extractDevice`、`addInventoryModels`、`setInventory`、`showRoom`、`inspectDevice`、`setExpanded`、`setDoor`、`setAlarm`、`animate`、`setMonitoring`。
- `src/views/home/scene/SOURCE.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 10 行。
- `src/views/home/scene/types.ts` — 所在模块的输入、状态及接口类型。约 33 行。
  - 本地依赖：`@/types/platform-identity`。
  - 公开方法/导出标识：`SceneInput`。
### src/views/home/scene/vendor

- `src/views/home/scene/vendor/OrbitControls.d.ts` — TypeScript 类型或第三方模块声明。约 2 行。
- `src/views/home/scene/vendor/OrbitControls.js` — 第三方轨道相机控制器。约 1144 行。
- `src/views/home/scene/vendor/three.module.d.ts` — TypeScript 类型或第三方模块声明。约 3 行。
- `src/views/home/scene/vendor/three.module.js` — 本地保存的第三方 Three.js 运行库。约 46155 行。
### src/views/home/scene

- `src/views/home/scene/zone-monitoring.ts` — 区域监测与场景状态同步。约 24 行。
  - 本地依赖：`./scene.ts`。
  - 公开方法/导出标识：`syncZoneMonitoring`。
### src/views/home/styles

- `src/views/home/styles/dashboard.scss` — 全局或业务局部样式。约 178 行。
### src/views/login

- `src/views/login/index.vue` — 页面或业务展示组件：views/login/index.vue。约 136 行。
  - 本地依赖：`@/services/console`。
### src/views/maintenance

- `src/views/maintenance/index.vue` — 页面或业务展示组件：views/maintenance/index.vue。约 539 行。
  - 本地依赖：`../../components/console-dialog-theme.css`、`@/services/console`、`@/services/work-orders`、`@/components/work-orders/WorkOrderCenter.vue`、`./PersonalAccount.vue`、`@/layouts/components/top/index.vue`。
  - 公开方法/导出标识：`navigate`、`openOrder`、`refresh`。
- `src/views/maintenance/PersonalAccount.vue` — 页面或业务展示组件：views/maintenance/PersonalAccount.vue。约 137 行。
  - 本地依赖：`@/services/console`。
  - 公开方法/导出标识：`save`。
### src/views/user

- `src/views/user/index.vue` — 页面或业务展示组件：views/user/index.vue。约 20 行。
  - 本地依赖：`@/components/ConsoleSettings.vue`。
### tests/platform

- `tests/platform/agent-confirmation.test.ts` — 前端逻辑测试或公共夹具：agent-confirmation.test.ts。约 65 行。
  - 本地依赖：`../../src/views/home/assistant/agent-conversation`。
  - 公开方法/导出标识：`fixture`。
- `tests/platform/agent-test-fixture.ts` — 前端逻辑测试或公共夹具：agent-test-fixture.ts。约 15 行。
  - 本地依赖：`../../src/views/home/assistant/agent-conversation`。
  - 公开方法/导出标识：`agentFixture`。
- `tests/platform/energy-alarms.test.ts` — 前端逻辑测试或公共夹具：energy-alarms.test.ts。约 13 行。
  - 本地依赖：`../../src/views/home/cockpit/energy/energy-alarms`。
- `tests/platform/home-scene-controls.test.ts` — 前端逻辑测试或公共夹具：home-scene-controls.test.ts。约 40 行。
  - 本地依赖：`../../src/views/home/assistant/home-scene-controls`。
- `tests/platform/identity-contract.test.ts` — 前端逻辑测试或公共夹具：identity-contract.test.ts。约 232 行。
  - 本地依赖：`../../src/utils/platform-identity`、`../../src/types/platform`。
  - 公开方法/导出标识：`fixture`。
- `tests/platform/inventory-adapter.test.ts` — 前端逻辑测试或公共夹具：inventory-adapter.test.ts。约 142 行。
- `tests/platform/legacy-assistant-removal.test.ts` — 前端逻辑测试或公共夹具：legacy-assistant-removal.test.ts。约 28 行。
  - 公开方法/导出标识：`scan`。
- `tests/platform/platform-display.test.ts` — 前端逻辑测试或公共夹具：platform-display.test.ts。约 25 行。
  - 本地依赖：`../../src/utils/platform-display`。
- `tests/platform/scene-action-executor.test.ts` — 前端逻辑测试或公共夹具：scene-action-executor.test.ts。约 68 行。
  - 本地依赖：`../../src/views/home/assistant/scene-action-executor`。
  - 公开方法/导出标识：`fixture`。
- `tests/platform/unified-data-source.test.ts` — 前端逻辑测试或公共夹具：unified-data-source.test.ts。约 22 行。
  - 本地依赖：`../../src/utils/unified-data-source`。
### .

- `tsconfig.app.json` — TypeScript 编译选项与项目引用。约 13 行。
- `tsconfig.json` — TypeScript 编译选项与项目引用。约 12 行。
- `tsconfig.node.json` — TypeScript 编译选项与项目引用。约 19 行。
- `visit-typecheck.log` — 本地执行日志，不属于运行时源码。约 5 行。
- `vite.config.ts` — Vite 插件、@ 路径别名与开发服务器配置。约 19 行。
- `未使用文件与代码清理清单.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 123 行。
- `项目结构说明.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 780 行。

## 后端逐文件清单

相对根目录：`后端/smart-park-guat-server`；共 95 个文件。

目录层级（文件完整路径见逐项清单）：

```text
后端/smart-park-guat-server/
  └─ database/
    └─ migrations/
    └─ tools/
  └─ scripts/
  └─ src/
    └─ main/
      └─ java/
        └─ cn/
          └─ guat/
            └─ smartpark/
              └─ common/
              └─ controller/
              └─ dto/
                └─ inventory/
              └─ entity/
              └─ exception/
              └─ mapper/
              └─ service/
      └─ resources/
    └─ test/
      └─ java/
        └─ cn/
          └─ guat/
            └─ smartpark/
              └─ service/
      └─ resources/
        └─ META-INF/
```

### database

- `database/inventory-demo-manifest.json` — 演示台账数据清单。约 625 行。
### database/migrations

- `database/migrations/README.md` — 数据库迁移/初始化脚本：README.md。约 34 行。
- `database/migrations/V001__inventory_schema.sql` — 数据库迁移/初始化脚本：V001__inventory_schema.sql。约 71 行。
- `database/migrations/V002__inventory_demo_seed.sql` — 数据库迁移/初始化脚本：V002__inventory_demo_seed.sql。约 60 行。
- `database/migrations/V003__console_schema.sql` — 数据库迁移/初始化脚本：V003__console_schema.sql。约 22 行。
- `database/migrations/V004__monitoring_schema.sql` — 数据库迁移/初始化脚本：V004__monitoring_schema.sql。约 21 行。
- `database/migrations/V005__integration_schema.sql` — 数据库迁移/初始化脚本：V005__integration_schema.sql。约 9 行。
- `database/migrations/V006__maintenance_profiles.sql` — 数据库迁移/初始化脚本：V006__maintenance_profiles.sql。约 3 行。
- `database/migrations/V007__assistant_session.sql` — 数据库迁移/初始化脚本：V007__assistant_session.sql。约 9 行。
- `database/migrations/V008__unified_data_source.sql` — 数据库迁移/初始化脚本：V008__unified_data_source.sql。约 16 行。
- `database/migrations/V009__owner_account_id.sql` — 数据库迁移/初始化脚本：V009__owner_account_id.sql。约 11 行。
### database/tools

- `database/tools/ExtendCabinetInventory.java` — 独立维护、校验、数据准备或审计工具：ExtendCabinetInventory.java。约 37 行。
  - 公开方法/导出标识：`main`。
- `database/tools/Stage1Database.java` — 独立维护、校验、数据准备或审计工具：Stage1Database.java。约 105 行。
  - 公开方法/导出标识：`main`。
### .

- `pom.xml` — Maven 依赖、Java 编译目标与 Spring Boot 打包配置。约 127 行。
### scripts

- `scripts/AppendTodayMonitoring.java` — 独立维护、校验、数据准备或审计工具：AppendTodayMonitoring.java。约 53 行。
  - 公开方法/导出标识：`main`。
- `scripts/BackfillAssetDashboard.java` — 独立维护、校验、数据准备或审计工具：BackfillAssetDashboard.java。约 50 行。
  - 公开方法/导出标识：`main`。
- `scripts/BackfillDailyEnergy.java` — 独立维护、校验、数据准备或审计工具：BackfillDailyEnergy.java。约 27 行。
  - 公开方法/导出标识：`main`。
- `scripts/BackfillLinkQuality.java` — 独立维护、校验、数据准备或审计工具：BackfillLinkQuality.java。约 39 行。
  - 公开方法/导出标识：`main`。
- `scripts/ClearAssistantHistory.java` — 独立维护、校验、数据准备或审计工具：ClearAssistantHistory.java。约 22 行。
  - 公开方法/导出标识：`main`。
- `scripts/NormalizeAlarms.java` — 独立维护、校验、数据准备或审计工具：NormalizeAlarms.java。约 67 行。
  - 公开方法/导出标识：`main`。
- `scripts/RebalanceCabinetEnergy.java` — 独立维护、校验、数据准备或审计工具：RebalanceCabinetEnergy.java。约 31 行。
  - 公开方法/导出标识：`main`。
- `scripts/SeedCabinetAlarms.java` — 独立维护、校验、数据准备或审计工具：SeedCabinetAlarms.java。约 46 行。
  - 公开方法/导出标识：`main`。
- `scripts/SeedMonitoring.java` — 独立维护、校验、数据准备或审计工具：SeedMonitoring.java。约 78 行。
  - 公开方法/导出标识：`main`。
- `scripts/SeedRoomTopology.java` — 独立维护、校验、数据准备或审计工具：SeedRoomTopology.java。约 53 行。
  - 公开方法/导出标识：`main`。
- `scripts/VerifyCabinetControls.java` — 独立维护、校验、数据准备或审计工具：VerifyCabinetControls.java。约 36 行。
  - 公开方法/导出标识：`main`。
- `scripts/VerifyIntegrationReads.java` — 独立维护、校验、数据准备或审计工具：VerifyIntegrationReads.java。约 20 行。
  - 公开方法/导出标识：`main`。
- `scripts/VerifyMonitoring.java` — 独立维护、校验、数据准备或审计工具：VerifyMonitoring.java。约 20 行。
  - 公开方法/导出标识：`main`。
- `scripts/VerifyRoomTopology.java` — 独立维护、校验、数据准备或审计工具：VerifyRoomTopology.java。约 37 行。
  - 公开方法/导出标识：`main`。
### src/main/java/cn/guat/smartpark/common

- `src/main/java/cn/guat/smartpark/common/R.java` — 统一 API 响应结构。约 119 行。
  - 公开方法/导出标识：`getRequestId`、`getServerTime`、`getError`、`success`、`error`、`getCode`、`getMsg`、`getData`。
### src/main/java/cn/guat/smartpark/controller

- `src/main/java/cn/guat/smartpark/controller/AdminInventoryController.java` — HTTP 接口控制器：AdminInventory（映射见下方源码标识）。约 27 行。
  - 声明依赖：InventoryMaintenanceService。
  - 公开方法/导出标识：`inventory`、`item`、`create`、`edit`、`delete`。
  - HTTP 映射：`@RequestMapping("/api/v1/admin")`；`@GetMapping("/{kind}") public R inventory(@PathVariable String kind,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="100")int pageSize){`；`@GetMapping("/{kind}/{id}") public R item(@PathVariable String kind,@PathVariable String id){return R.success(view(inventory.get(kind,id)));}`；`@PostMapping("/{kind}") public R create(@PathVariable String kind,@RequestBody Map<String,Object>b){return R.success(view(inventory.save(kind,null,b)));}`；`@PutMapping("/{kind}/{id}") public R edit(@PathVariable String kind,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(view(inventory.save(kind,id,b)));}`；`@DeleteMapping("/{kind}/{id}") public R delete(@PathVariable String kind,@PathVariable String id){inventory.delete(kind,id);return R.success();}`。
- `src/main/java/cn/guat/smartpark/controller/ApiErrorController.java` — HTTP 接口控制器：ApiError（映射见下方源码标识）。约 23 行。
  - 公开方法/导出标识：`error`。
  - HTTP 映射：`@RequestMapping("/error")`。
- `src/main/java/cn/guat/smartpark/controller/AssistantAgentController.java` — HTTP 接口控制器：AssistantAgent（映射见下方源码标识）。约 41 行。
  - 声明依赖：AssistantAgentService、ConsoleService、ObjectMapper。
  - 公开方法/导出标识：`end`、`current`、`confirm`、`confirmationStatus`、`dismiss`、`chat`、`scene`。
  - HTTP 映射：`@RequestMapping("/api/v1/assistant/rooms/{room}")`；`@PostMapping("/session/end") public R end(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room){agent.end(actor(h,page),room);return R.success(object());}`；`@GetMapping("/session") public R current(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room){return R.success(agent.current(actor(h,page),room));}`；`@PostMapping("/confirm") public R confirm(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.confirm(actor(h,page),room,b));}`；`@PostMapping("/confirmation/status") public R confirmationStatus(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.confirmationStatus(actor(h,page),room,b));}`；`@PostMapping("/pending/dismiss") public R dismiss(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.dismissPending(actor(h,page),room,b));}`；`@PostMapping("/chat") public Object chat(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response){return reply(h,page,room,b,response,false);}`；`@PostMapping("/scene-result") public Object scene(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response){return reply(h,page,room,b,response,true);}`。
- `src/main/java/cn/guat/smartpark/controller/AuthController.java` — HTTP 接口控制器：Auth（映射见下方源码标识）。约 19 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`login`、`current`、`logout`、`password`。
  - HTTP 映射：`@RequestMapping("/api/v1/auth")`；`@PostMapping("/login") public R login(@RequestBody Map<String,Object>b){return R.success(service.login(b));}`；`@GetMapping("/me") public R current(@RequestHeader(value="Authorization",required=false)String h){return R.success(me(h));}`；`@PostMapping("/logout") public R logout(@RequestHeader(value="Authorization",required=false)String h){service.logout(h);return R.success();}`；`@PutMapping("/password") public R password(@RequestHeader("Authorization")String h,@RequestBody Map<String,Object>b){service.changePassword(me(h),b);return R.success();}`。
- `src/main/java/cn/guat/smartpark/controller/CabinetController.java` — HTTP 接口控制器：Cabinet（映射见下方源码标识）。约 14 行。
  - 声明依赖：InventoryService。
  - 公开方法/导出标识：`cabinet`、`devices`。
  - HTTP 映射：`@RequestMapping("/api/v1/cabinets")`；`@GetMapping("/{cabinetId}") public R<Object> cabinet(@PathVariable String cabinetId){return ok(service.cabinet(cabinetId));}`；`@GetMapping("/{cabinetId}/devices") public R<Object> devices(@PathVariable String cabinetId,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String type,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort){return ok(service.devices(cabinetId,q,type,page,pageSize,sort));}`。
- `src/main/java/cn/guat/smartpark/controller/IntegrationController.java` — HTTP 接口控制器：Integration（映射见下方源码标识）。约 27 行。
  - 声明依赖：IntegrationService、ConsoleService、DemoMonitoringService。
  - 公开方法/导出标识：`capabilities`、`collectorStatus`、`collect`、`ingest`、`demoTemperature`、`history`、`rules`、`rule`、`deleteRule`、`evaluate`、`configureVideo`、`video`、`command`、`commands`。
  - HTTP 映射：`@RequestMapping("/api/v1")`；`@GetMapping("/integrations") public R capabilities(){return R.success(service.capabilities());}`；`@GetMapping("/integrations/collector") public R collectorStatus(){return R.success(service.collectorStatus());}`；`@PostMapping("/integrations/collector/pull") public R collect(){return R.success(service.collect());}`；`@PostMapping("/rooms/{room}/ingest") public R ingest(@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(service.ingest(room,b));}`；`@PostMapping("/rooms/{room}/demo-monitoring/temperature") public R demoTemperature(@PathVariable String room,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b){return R.success(demo.submit(room,b,console.identity(auth)));}`；`@GetMapping("/rooms/{room}/readings/history") public R history(@PathVariable String room,@RequestParam String entityType,@RequestParam String entityId,@RequestParam String metric,@RequestParam String from,@RequestParam String to){return R.success(service.history(room,entityType,entityId,metric,from,to));}`；`@GetMapping("/rooms/{room}/alarm-rules") public R rules(@PathVariable String room){return R.success(service.rules(room));}`；`@PutMapping("/rooms/{room}/alarm-rules/{id}") public R rule(@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(service.saveRule(room,id,b));}`；`@DeleteMapping("/rooms/{room}/alarm-rules/{id}") public R deleteRule(@PathVariable String room,@PathVariable String id){service.deleteRule(room,id);return R.success();}`；`@PostMapping("/rooms/{room}/alarms/evaluate") public R evaluate(@PathVariable String room,@RequestParam(defaultValue="REAL")String recordOrigin){return R.success(service.evaluate(room,recordOrigin));}`；`@PutMapping("/rooms/{room}/cameras/{id}/video") public R configureVideo(@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(service.videoConfig(room,id,b));}`；`@GetMapping("/rooms/{room}/cameras/{id}/video") public R video(@PathVariable String room,@PathVariable String id){return R.success(service.video(room,id));}`；`@PostMapping("/rooms/{room}/cabinets/{id}/commands") public R command(@PathVariable String room,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b){return R.success(service.command(room,id,b,console.identity(auth)));}`；`@GetMapping("/rooms/{room}/cabinets/{id}/commands") public R commands(@PathVariable String room,@PathVariable String id){return R.success(service.commands(room,id));}`。
- `src/main/java/cn/guat/smartpark/controller/MonitoringController.java` — HTTP 接口控制器：Monitoring（映射见下方源码标识）。约 23 行。
  - 声明依赖：MonitoringService、ConsoleService。
  - 公开方法/导出标识：`control`、`snapshot`、`readings`、`alarms`、`facilities`、`topology`、`logs`、`energy`、`acknowledge`。
  - HTTP 映射：`@RequestMapping("/api/v1/rooms/{roomId}")`；`@PutMapping("/cabinets/{id}/controls") public R control(@PathVariable String roomId,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> body){return R.success(service.control(roomId,id,body,console.identity(auth)));}`；`@GetMapping("/monitoring") public R snapshot(@PathVariable String roomId){return R.success(service.snapshot(roomId));}`；`@GetMapping("/readings") public R readings(@PathVariable String roomId){return R.success(service.readings(roomId));}`；`@GetMapping("/alarms") public R alarms(@PathVariable String roomId){return R.success(service.records(roomId,"ALARM"));}`；`@GetMapping("/facilities") public R facilities(@PathVariable String roomId){return R.success(service.records(roomId,"FACILITY"));}`；`@GetMapping("/network/topology") public R topology(@PathVariable String roomId){return R.success(cn.guat.smartpark.dto.inventory.InventoryDtos.object("roomId",roomId,"nodes",service.records(roomId,"NODE"),"links",service.records(roomId,"LINK")));}`；`@GetMapping("/operation-logs") public R logs(@PathVariable String roomId){return R.success(service.records(roomId,"LOG"));}`；`@GetMapping("/energy") public R energy(@PathVariable String roomId,@RequestParam(defaultValue="today")String period){return R.success(service.energy(roomId,period));}`；`@PutMapping("/alarms/{id}/acknowledge") public R acknowledge(@PathVariable String roomId,@PathVariable String id){return R.success(service.acknowledge(roomId,id));}`。
- `src/main/java/cn/guat/smartpark/controller/NotificationController.java` — HTTP 接口控制器：Notification（映射见下方源码标识）。约 18 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`notices`、`read`、`deleteRead`。
  - HTTP 映射：`@RequestMapping("/api/v1/notifications")`；`@PutMapping("/{id}/read") public R read(@RequestHeader("Authorization")String h,@PathVariable Long id){service.readNotice(me(h),id);return R.success();}`；`@DeleteMapping("/read") public R deleteRead(@RequestHeader("Authorization")String h){service.deleteRead(me(h));return R.success();}`。
- `src/main/java/cn/guat/smartpark/controller/RoomController.java` — HTTP 接口控制器：Room（映射见下方源码标识）。约 16 行。
  - 声明依赖：InventoryService。
  - 公开方法/导出标识：`rooms`、`room`、`scene`、`cabinets`。
  - HTTP 映射：`@RequestMapping("/api/v1/rooms")`；`@GetMapping("/{roomId}") public R<Object> room(@PathVariable String roomId){return ok(service.room(roomId));}`；`@GetMapping("/{roomId}/scene") public R<Object> scene(@PathVariable String roomId,@RequestParam(defaultValue="REAL") String recordOrigin){return ok(service.scene(roomId,recordOrigin));}`；`@GetMapping("/{roomId}/cabinets") public R<Object> cabinets(@PathVariable String roomId,@RequestParam(required=false) String zoneId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort){return ok(service.cabinets(roomId,zoneId,q,page,pageSize,sort));}`。
- `src/main/java/cn/guat/smartpark/controller/UserController.java` — HTTP 接口控制器：User（映射见下方源码标识）。约 19 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`people`、`add`、`edit`、`delete`。
  - HTTP 映射：`@RequestMapping("/api/v1/users")`；`@PutMapping("/{id}") public R edit(@RequestHeader("Authorization")String h,@PathVariable Long id,@RequestBody Map<String,Object>b){service.savePerson(me(h),b,id);return R.success();}`；`@DeleteMapping("/{id}") public R delete(@RequestHeader("Authorization")String h,@PathVariable Long id){service.deletePerson(me(h),id);return R.success();}`。
- `src/main/java/cn/guat/smartpark/controller/WeatherController.java` — HTTP 接口控制器：Weather（映射见下方源码标识）。约 26 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`weather`。
  - HTTP 映射：`@RequestMapping("/api/v1/weather")`。
- `src/main/java/cn/guat/smartpark/controller/WorkOrderController.java` — HTTP 接口控制器：WorkOrder（映射见下方源码标识）。约 26 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`orders`、`history`、`create`、`claim`、`assign`、`start`、`save`、`submit`、`retest`。
  - HTTP 映射：`@RequestMapping("/api/v1/work-orders")`；`@GetMapping("/history") public R history(@RequestHeader("Authorization")String auth,@RequestParam String roomId,@RequestParam(required=false)String cabinetId,@RequestParam(required=false)String deviceId,@RequestParam(required=false)String recordOrigin){return R.success(service.history(service.identity(auth),roomId,cabinetId,deviceId,recordOrigin));}`；`@PostMapping("/{id}/claim") public R claim(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"claim",body);}`；`@PostMapping("/{id}/assign") public R assign(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Assignment body){return action(auth,id,"assign",body);}`；`@PostMapping("/{id}/start") public R start(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"start",body);}`；`@PatchMapping("/{id}/processing-record") public R save(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody ProcessingRecord body){return action(auth,id,"save",body);}`；`@PostMapping("/{id}/submit") public R submit(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Submission body){return action(auth,id,"submit",body);}`；`@PostMapping("/{id}/retest") public R retest(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"retest",body);}`。
### src/main/java/cn/guat/smartpark/dto/inventory

- `src/main/java/cn/guat/smartpark/dto/inventory/InventoryDtos.java` — 台账响应数据组装。约 10 行。
  - 公开方法/导出标识：`object`。
### src/main/java/cn/guat/smartpark/dto

- `src/main/java/cn/guat/smartpark/dto/WorkOrderRequests.java` — 工单操作请求及嵌套类型。约 39 行。
  - 公开方法/导出标识：`toMap`。
### src/main/java/cn/guat/smartpark/entity

- `src/main/java/cn/guat/smartpark/entity/Cabinet.java` — 台账实体：Cabinet。约 5 行。
- `src/main/java/cn/guat/smartpark/entity/Device.java` — 台账实体：Device。约 5 行。
- `src/main/java/cn/guat/smartpark/entity/InventoryRecord.java` — 台账实体：InventoryRecord。约 9 行。
- `src/main/java/cn/guat/smartpark/entity/Park.java` — 台账实体：Park。约 3 行。
- `src/main/java/cn/guat/smartpark/entity/Room.java` — 台账实体：Room。约 6 行。
- `src/main/java/cn/guat/smartpark/entity/Zone.java` — 台账实体：Zone。约 5 行。
### src/main/java/cn/guat/smartpark/exception

- `src/main/java/cn/guat/smartpark/exception/PlatformException.java` — 平台业务异常定义。约 6 行。
- `src/main/java/cn/guat/smartpark/exception/PlatformExceptionHandler.java` — 统一异常到 HTTP 响应转换。约 20 行。
  - 公开方法/导出标识：`business`、`argument`、`method`、`duplicate`、`database`、`unexpected`。
### src/main/java/cn/guat/smartpark/mapper

- `src/main/java/cn/guat/smartpark/mapper/InventoryMapper.java` — MyBatis 台账查询与数据库映射。约 16 行。
### src/main/java/cn/guat/smartpark/service

- `src/main/java/cn/guat/smartpark/service/AssistantAgentService.java` — AI 助手会话、工具调用、流式输出与确认流程。约 328 行。
  - 声明依赖：JdbcTemplate、ObjectMapper、IntegrationService、ConsoleService、MonitoringService。
  - 公开方法/导出标识：`setAssetTools`、`setPriorityAlarms`、`schema`、`stream`、`end`、`current`、`chat`、`confirm`、`confirmationStatus`、`dismissPending`、`sceneResult`、`tools`。
- `src/main/java/cn/guat/smartpark/service/AssistantAssetTools.java` — 助手资产查询和工具适配。约 55 行。
  - 声明依赖：InventoryService。
  - 公开方法/导出标识：`supports`、`execute`、`definitions`。
- `src/main/java/cn/guat/smartpark/service/AssistantPriorityAlarms.java` — 助手告警优先级与告警摘要。约 52 行。
  - 声明依赖：MonitoringService、InventoryMapper。
  - 公开方法/导出标识：`execute`、`definition`。
- `src/main/java/cn/guat/smartpark/service/CollectorScheduler.java` — 周期触发采集任务。约 18 行。
  - 声明依赖：IntegrationService。
  - 公开方法/导出标识：`poll`。
- `src/main/java/cn/guat/smartpark/service/ConsoleAccessFilter.java` — API 会话与角色访问过滤器。约 30 行。
  - 声明依赖：ConsoleService、ObjectMapper。
- `src/main/java/cn/guat/smartpark/service/ConsoleService.java` — 账号、认证会话、人员、通知、工单与复检业务。约 127 行。
  - 声明依赖：JdbcTemplate、ObjectMapper、MonitoringService。
  - 公开方法/导出标识：`schema`、`login`、`identity`、`admin`、`logout`、`changePassword`、`people`、`savePerson`、`deletePerson`、`orders`、`history`、`createOrder`、`action`、`pendingRetests`、`automaticRetest`、`notices`、`readNotice`、`deleteRead`。
- `src/main/java/cn/guat/smartpark/service/DemoMonitoringService.java` — 演示监测读写与模拟温度业务。约 48 行。
  - 声明依赖：JdbcTemplate、MonitoringService、IntegrationService、DemoTemperaturePolicy、ConsoleService、ObjectMapper。
  - 公开方法/导出标识：`submit`。
- `src/main/java/cn/guat/smartpark/service/DemoTemperaturePolicy.java` — 演示温度策略。约 31 行。
  - 声明依赖：JdbcTemplate。
  - 公开方法/导出标识：`cabinet`、`origin`、`managed`、`validateRule`、`bad`。
- `src/main/java/cn/guat/smartpark/service/EnergyBackfillService.java` — 能耗历史数据补写。约 51 行。
  - 声明依赖：JdbcTemplate。
  - 公开方法/导出标识：`fill`。
- `src/main/java/cn/guat/smartpark/service/IntegrationService.java` — 采集、规则、模型、视频及控制外部集成。约 178 行。
  - 声明依赖：JdbcTemplate、MonitoringService、ObjectMapper、DemoTemperaturePolicy、RestTemplate。
  - 公开方法/导出标识：`schema`、`text`、`entity`、`rules`、`saveRule`、`deleteRule`、`ingest`、`evaluate`、`history`、`capabilities`、`modelTools`、`modelToolsStream`、`collectorStatus`、`collect`、`videoConfig`、`video`、`command`、`commands`。
- `src/main/java/cn/guat/smartpark/service/InventoryMaintenanceService.java` — 台账新增修改删除、字段与机柜 U 位约束。约 74 行。
  - 声明依赖：JdbcTemplate。
  - 公开方法/导出标识：`list`、`get`、`save`、`delete`。
- `src/main/java/cn/guat/smartpark/service/InventoryService.java` — 台账查询、详情与场景数据组装。约 67 行。
  - 声明依赖：InventoryMapper、MonitoringService。
  - 公开方法/导出标识：`rooms`、`room`、`scene`、`cabinets`、`cabinet`、`devices`、`scopedCabinet`、`device`。
- `src/main/java/cn/guat/smartpark/service/MonitoringService.java` — 监测读数、告警、能耗及控制状态管理。约 107 行。
  - 声明依赖：JdbcTemplate、ObjectMapper、DemoTemperaturePolicy、EnergyBackfillService。
  - 公开方法/导出标识：`schema`、`room`、`lockRoom`、`records`、`readings`、`snapshot`、`control`、`reading`、`energy`、`acknowledge`、`saveRecord`、`demoRetest`、`retest`。
- `src/main/java/cn/guat/smartpark/service/UnifiedDataSource.java` — 统一数据来源规则。约 19 行。
  - 公开方法/导出标识：`origin`、`normalize`、`note`。
- `src/main/java/cn/guat/smartpark/service/UnifiedDataSourceMigration.java` — 数据来源迁移与启动执行逻辑。约 53 行。
  - 声明依赖：JdbcTemplate、ObjectMapper、TransactionTemplate。
  - 公开方法/导出标识：`run`、`schema`、`migrate`。
- `src/main/java/cn/guat/smartpark/service/WorkOrderRetestScheduler.java` — 周期触发工单自动复测。约 17 行。
  - 声明依赖：ConsoleService。
  - 公开方法/导出标识：`inspect`。
### src/main/java/cn/guat

- `src/main/java/cn/guat/SmartparkApplication.java` — Spring Boot 启动入口。约 26 行。
  - 公开方法/导出标识：`main`。
### src/main/resources

- `src/main/resources/application-demo-development.yml` — Spring 环境配置，仅说明职责，不复制连接信息或凭据。约 6 行。
- `src/main/resources/application-integrations.yml` — Spring 环境配置，仅说明职责，不复制连接信息或凭据。约 33 行。
- `src/main/resources/application.yml` — Spring 环境配置，仅说明职责，不复制连接信息或凭据。约 23 行。
- `src/main/resources/准备工作.txt` — 后端运行准备说明。约 87 行。
### src/test/java/cn/guat/smartpark

- `src/test/java/cn/guat/smartpark/ApiProtocolTest.java` — 后端测试或测试支持：ApiProtocolTest。约 56 行。
- `src/test/java/cn/guat/smartpark/AssistantAgentIntegrationTest.java` — 后端测试或测试支持：AssistantAgentIntegrationTest。约 197 行。
- `src/test/java/cn/guat/smartpark/AssistantApiRemovalTest.java` — 后端测试或测试支持：AssistantApiRemovalTest。约 59 行。
- `src/test/java/cn/guat/smartpark/AssistantAssetToolsTest.java` — 后端测试或测试支持：AssistantAssetToolsTest。约 66 行。
- `src/test/java/cn/guat/smartpark/AssistantAssignmentTest.java` — 后端测试或测试支持：AssistantAssignmentTest。约 98 行。
- `src/test/java/cn/guat/smartpark/AssistantPriorityAlarmsTest.java` — 后端测试或测试支持：AssistantPriorityAlarmsTest。约 76 行。
- `src/test/java/cn/guat/smartpark/AssistantSceneToolsTest.java` — 后端测试或测试支持：AssistantSceneToolsTest。约 41 行。
- `src/test/java/cn/guat/smartpark/AssistantUnifiedAgentTest.java` — 后端测试或测试支持：AssistantUnifiedAgentTest。约 110 行。
- `src/test/java/cn/guat/smartpark/ConsoleIntegrationTest.java` — 后端测试或测试支持：ConsoleIntegrationTest。约 161 行。
- `src/test/java/cn/guat/smartpark/DemoMonitoringIntegrationTest.java` — 后端测试或测试支持：DemoMonitoringIntegrationTest。约 157 行。
- `src/test/java/cn/guat/smartpark/EnergyBackfillIntegrationTest.java` — 后端测试或测试支持：EnergyBackfillIntegrationTest。约 54 行。
- `src/test/java/cn/guat/smartpark/IntegrationApiTest.java` — 后端测试或测试支持：IntegrationApiTest。约 48 行。
- `src/test/java/cn/guat/smartpark/IntegrationGatewayTest.java` — 后端测试或测试支持：IntegrationGatewayTest。约 60 行。
- `src/test/java/cn/guat/smartpark/InventoryControllerTest.java` — 后端测试或测试支持：InventoryControllerTest。约 43 行。
- `src/test/java/cn/guat/smartpark/InventoryFailureResponseTest.java` — 后端测试或测试支持：InventoryFailureResponseTest。约 26 行。
- `src/test/java/cn/guat/smartpark/InventoryMapperIntegrationTest.java` — 后端测试或测试支持：InventoryMapperIntegrationTest。约 25 行。
- `src/test/java/cn/guat/smartpark/ModelStreamingTest.java` — 后端测试或测试支持：ModelStreamingTest。约 58 行。
### src/test/java/cn/guat/smartpark/service

- `src/test/java/cn/guat/smartpark/service/ConsoleIdentityNamingTest.java` — 后端测试或测试支持：ConsoleIdentityNamingTest。约 34 行。
### src/test/java/cn/guat/smartpark

- `src/test/java/cn/guat/smartpark/TestDatabaseIsolationInitializer.java` — 后端测试或测试支持：TestDatabaseIsolationInitializer。约 13 行。
  - 公开方法/导出标识：`initialize`。
- `src/test/java/cn/guat/smartpark/UnifiedDataSourceIntegrationTest.java` — 后端测试或测试支持：UnifiedDataSourceIntegrationTest。约 46 行。
### src/test/resources

- `src/test/resources/application-test.yml` — 后端测试或测试支持：application-test.yml。约 4 行。
### src/test/resources/META-INF

- `src/test/resources/META-INF/spring.factories` — 后端测试或测试支持：spring.factories。约 2 行。

## 根数据库逐文件清单

相对根目录：`数据库`；共 5 个文件。

目录层级（文件完整路径见逐项清单）：

```text
数据库/
```

### .

- `.keep` — 空目录占位文件。约 0 行。
- `ID命名规范.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 28 行。
- `platform_smart_park.sql` — 数据库 DDL/DML 脚本。约 118 行。
- `README.md` — 项目/模块说明或历史清理记录，以当前源码为准。约 6 行。
- `user.sql` — 数据库 DDL/DML 脚本。约 44 行。

## 排除目录记录

- `前端/smart-park-guat-web/.vscode`：依赖、产物、测试结果或IDE元数据。
- `前端/smart-park-guat-web/dist`：依赖、产物、测试结果或IDE元数据。
- `前端/smart-park-guat-web/node_modules`：依赖、产物、测试结果或IDE元数据。
- `前端/smart-park-guat-web/test-results`：依赖、产物、测试结果或IDE元数据。
- `后端/smart-park-guat-server/.idea`：依赖、产物、测试结果或IDE元数据。
- `后端/smart-park-guat-server/target`：依赖、产物、测试结果或IDE元数据。

## 可直接发给 GPT 的审查提示词

```text
请根据这份当前项目结构文档，审查智慧园区/机房管理系统的前后端组织是否合理。
先归纳现有架构及业务边界，再检查目录分层、文件职责、超大文件、模块耦合、组件复用、状态管理、API 契约、数据库迁移和测试组织。
请把有源码结构证据的问题与需要进一步读取源码才能判断的问题分开。不要仅凭目录名断言代码有 bug，也不要把第三方 vendor 代码当作业务代码。
按优先级列出改进项，每项给出现有文件、具体原因、建议归属和最小迁移步骤。提供建议目录树，并给出主要旧文件到新位置的映射。
保留现有功能和技术栈，优先渐进重构。不要默认引入微服务、DDD 全套、额外状态库或重写三维引擎。
如果信息不足，请列出最需要继续查看的 5–10 个源码文件及原因。
```
