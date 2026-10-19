# 智慧机房管控系统 · 前端

这是 Vue 3 + TypeScript/JavaScript 的机房管理前端，包含管理员大屏、三维机房、巡检助手和维保后台。

## 代码格式

项目已固定 Prettier 版本并使用 `.prettierrc.json` 统一规则。在本目录执行：

```sh
npm ci
npm run format
npm run format:check
```

`npm ci` 用于首次安装依赖；`format` 批量整理格式，`format:check` 只检查格式，可用于 CI。
格式化覆盖前端源码、测试、脚本及支持的配置和文档，忽略依赖、构建产物、测试报告、公共资源和锁文件。

VS Code 中安装推荐扩展 **Prettier - Code formatter**（`esbenp.prettier-vscode`），保存文件时即可自动格式化。
仓库根目录和前端目录均已配置保存时格式化，统一使用项目配置。Prettier 默认不负责 Java、SQL 的格式化。

## 从哪里开始看

- [完整目录与逐文件职责](./项目结构说明.md)
- [首页业务代码导航](./src/views/home/README.md)
- [历史清理依据和执行记录](./未使用文件与代码清理清单.md)

```text
src/
├─ main.ts、App.vue    应用启动与根视图
├─ router/            页面地址、登录校验和角色跳转
├─ layouts/           顶部栏、导航和整体布局
├─ api/platform/      后端平台接口
├─ services/          登录会话、工单、天气等跨页面业务
├─ stores/            台账、监测和菜单的共享状态
├─ types/             共享类型和身份契约
├─ utils/             时间、身份、等级和展示转换
├─ components/        跨页面公共组件
├─ assets/            构建时处理的图片与全局样式
└─ views/
   ├─ login/          登录页
   ├─ user/           人员管理入口
   ├─ maintenance/    维保人员后台
   └─ home/           管理员大屏
      ├─ cockpit/     room、cabinet、energy、alarms、assets、topology 六类业务
      ├─ composables/ 首页导航、数据同步、机柜控制
      ├─ components/  首页共享 UI
      ├─ scene/       三维模型、动画和数据适配
      ├─ assistant/   对话、语音、巡检与建单
      ├─ demo/        仍被使用的演示和兼容数据
      └─ styles/      首页共用布局样式
```

## 开发与验证

Node 版本要求见 package.json。首次运行 npm install，然后：

```sh
npm run dev
npm run type-check
npm run build
```

后端地址在 .env.development 的 VITE_APP_BASE_API 中配置。页面角色由登录会话决定；首页业务菜单通过 business 查询参数切换。

测试目录：e2e/platform 放浏览器业务流程，tests/platform 放身份、数据适配等回归测试。Playwright 默认访问 http://127.0.0.1:5174；启动对应端口的前端或通过 STAGE1_BASE_URL 指定地址。

```sh
npm run test:stage1
node scripts/audit-unused.mjs
```

## 新代码放置约定

- 页面入口组织布局；状态、异步请求和复杂交互放 composables 或业务服务。
- 卡片按用途命名，例如 RoomInfoCard、DeviceListCard、ElectricalAlarmsCard，不使用 LeftTop 等位置名。
- 单业务组件放所属 cockpit 模块；首页共享组件放 home/components；跨页面组件放 src/components。
- API 请求封装放 api，共享数据状态放 stores。services 中保留现有后台会话、工单和天气服务。
- 图片通过模板、样式、配置和动态加载使用，删除前检查引用链。不要手工修改 node_modules 和 dist。

本次目录整理保持原路由、卡片布局、三维交互和业务接口。资产页已拆成布局入口、useAssetDashboard.js 和 assets.css；机柜控制已独立为 useCabinetControls.ts。

## 后端数据库凭据

数据库账号可通过 DB_USERNAME 设置，密码通过 DB_PASSWORD 设置。开发机也可使用后端 src/main/resources/application-local.yml（已忽略），在 spring.datasource 下配置本机凭据；不要提交该文件。
