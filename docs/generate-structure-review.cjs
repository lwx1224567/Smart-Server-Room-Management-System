const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const output = path.join(__dirname, '前后端项目结构详解-GPT审查版.md');
const excluded = new Set(['node_modules', '.git', 'target', 'dist', '.idea', '.vscode', 'test-results', 'playwright-report']);
const omitted = [];
function walk(dir) {
  return fs.readdirSync(dir, {withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name,'en')).flatMap(e=>{
    const p=path.join(dir,e.name);
    if(e.isSymbolicLink()) { omitted.push([path.relative(root,p),'符号链接，不递归']); return []; }
    if(e.isDirectory() && excluded.has(e.name)) { omitted.push([path.relative(root,p),'依赖、产物、测试结果或IDE元数据']); return []; }
    return e.isDirectory()?walk(p):[p];
  });
}
const names = {
 'menu.ts':'业务菜单项目与切换配置', 'alarm-levels.ts':'告警级别转换和显示工具', 'order-time.ts':'工单时间处理工具',
 'legacy-demo-scene-bridge.ts':'旧演示数据与场景的桥接适配', 'mockData.js':'演示机房与设备数据', 'topologyData.ts':'演示网络拓扑数据',
 'types.ts':'所在模块的输入、状态及接口类型', 'inventory-demo-manifest.json':'演示台账数据清单', '准备工作.txt':'后端运行准备说明', '.keep':'空目录占位文件',
 'main.ts':'Vue 应用启动，注册 Pinia、路由、Element Plus 与全局样式', 'App.vue':'Vue 根组件',
 'package.json':'前端依赖、运行环境要求与 npm 命令', 'package-lock.json':'npm 依赖解析锁定文件', 'pom.xml':'Maven 依赖、Java 编译目标与 Spring Boot 打包配置',
 'vite.config.ts':'Vite 插件、@ 路径别名与开发服务器配置', 'playwright.config.ts':'Playwright 测试配置',
 'env.d.ts':'前端环境及模块类型声明', 'index.html':'浏览器 HTML 入口',
 'SmartparkApplication.java':'Spring Boot 启动入口', 'R.java':'统一 API 响应结构',
 'InventoryMapper.java':'MyBatis 台账查询与数据库映射', 'InventoryService.java':'台账查询、详情与场景数据组装',
 'InventoryMaintenanceService.java':'台账新增修改删除、字段与机柜 U 位约束',
 'ConsoleService.java':'账号、认证会话、人员、通知、工单与复检业务',
 'ConsoleAccessFilter.java':'API 会话与角色访问过滤器',
 'MonitoringService.java':'监测读数、告警、能耗及控制状态管理',
 'IntegrationService.java':'采集、规则、模型、视频及控制外部集成',
 'AssistantAgentService.java':'AI 助手会话、工具调用、流式输出与确认流程',
 'AssistantAssetTools.java':'助手资产查询和工具适配', 'AssistantPriorityAlarms.java':'助手告警优先级与告警摘要',
 'CollectorScheduler.java':'周期触发采集任务', 'WorkOrderRetestScheduler.java':'周期触发工单自动复测',
 'UnifiedDataSource.java':'统一数据来源规则', 'UnifiedDataSourceMigration.java':'数据来源迁移与启动执行逻辑',
 'DemoMonitoringService.java':'演示监测读写与模拟温度业务', 'DemoTemperaturePolicy.java':'演示温度策略', 'EnergyBackfillService.java':'能耗历史数据补写',
 'PlatformException.java':'平台业务异常定义', 'PlatformExceptionHandler.java':'统一异常到 HTTP 响应转换',
 'InventoryDtos.java':'台账响应数据组装', 'WorkOrderRequests.java':'工单操作请求及嵌套类型',
 'RoomScene.vue':'三维场景 Vue 容器、生命周期和交互桥接', 'scene.ts':'Three.js 场景建模、渲染、拾取、交互、动画和资源释放',
 'platform-scene-adapter.ts':'后端台账到三维模型的转换及身份映射', 'patrol.ts':'巡检人物建模和移动',
 'cameras.ts':'摄像头配置', 'facilities.ts':'场景设施配置', 'icons.ts':'场景 SVG 图标',
 'cabinetState.ts':'演示机柜控制和告警状态', 'cabinet-monitoring.ts':'机柜监测与场景状态同步', 'zone-monitoring.ts':'区域监测与场景状态同步',
 'three.module.js':'本地保存的第三方 Three.js 运行库', 'OrbitControls.js':'第三方轨道相机控制器',
 'useHomeDashboard.ts':'首页驾驶舱数据加载、刷新及功能能力协调', 'useHomeSceneData.ts':'台账和监测数据到场景的响应式连接', 'useCabinetControls.ts':'机柜操作及控制请求协调',
 'agent-client.ts':'助手 HTTP/SSE 传输', 'agent-assistant.ts':'助手交互协调', 'agent-conversation.ts':'助手对话状态和消息处理',
 'scene-action-executor.ts':'执行助手发出的浏览器场景动作', 'home-scene-controls.ts':'首页可供助手调用的场景控制接口',
 'voice-turn.js':'语音轮次处理', 'voice-turn.d.ts':'语音轮次模块类型声明', 'InspectionAssistant.vue':'巡检助手交互界面', 'AssistantMarkdown.vue':'助手 Markdown 消息渲染',
 'useAssetDashboard.js':'资产驾驶舱组合逻辑', 'card-groups.ts':'驾驶舱卡片分组配置',
 'platform-display.ts':'台账展示格式与文案工具', 'platform-identity.ts':'台账/场景身份类型或解析工具，按所在目录区分', 'unified-data-source.ts':'统一数据来源前端规则',
 'energy-data.ts':'能耗图表数据与周期状态', 'energy-alarms.ts':'能耗相关告警处理',
 'topology-layout.ts':'拓扑布局', 'topology-search.ts':'拓扑检索', 'topology-drilldown.ts':'拓扑层级下钻逻辑',
 'console.ts':'认证会话、fetch 请求封装及控制台 API', 'work-orders.ts':'工单前端服务与共享状态', 'weather.ts':'天气服务调用',
 'contracts.ts':'API 通用响应契约', 'client.ts':'平台 API 客户端适配', 'inventory.ts':'台账 API', 'integrations.ts':'集成 API', 'demo-monitoring.ts':'演示监测 API',
};
function role(rel) {
 const n=path.basename(rel), ext=path.extname(n);
 if(names[n]) return names[n];
 if(rel.includes('/router/')) return '路由定义、登录守卫与角色跳转';
 if(rel.includes('/stores/')) return ({'platform.ts':'台账、场景、机柜和身份索引 Pinia 状态','monitoring.ts':'监测、告警与能耗 Pinia 状态','menu.ts':'菜单 Pinia 状态'})[n]||'Pinia 状态模块';
 if(rel.includes('/entity/')) return '台账实体：'+n.replace('.java','');
 if(rel.includes('/controller/')) return 'HTTP 接口控制器：'+n.replace('Controller.java','')+'（映射见下方源码标识）';
 if(rel.includes('/src/test/')) return '后端测试或测试支持：'+n.replace('.java','');
 if(rel.includes('/e2e/')) return '浏览器端到端测试或公共夹具：'+n;
 if(rel.includes('/tests/')) return '前端逻辑测试或公共夹具：'+n;
 if(rel.includes('/migrations/')) return '数据库迁移/初始化脚本：'+n;
 if(rel.includes('/scripts/')||rel.includes('/database/tools/')) return '独立维护、校验、数据准备或审计工具：'+n;
 if(n.startsWith('.env')) return '前端环境配置，仅列文件名，不复制变量值';
 if(n.startsWith('application') && /ya?ml$/.test(n)) return 'Spring 环境配置，仅说明职责，不复制连接信息或凭据';
 if(n.startsWith('tsconfig')) return 'TypeScript 编译选项与项目引用';
 if(n.includes('gitignore')) return 'Git 忽略规则';
 if(n.includes('prettier')) return 'Prettier 格式配置或忽略规则';
 if(n==='.gitattributes') return 'Git 文件属性和换行规则';
 if(n==='index.ts') return '同目录模块集中导出入口';
 if(ext==='.vue') return rel.includes('/views/')?'页面或业务展示组件：'+rel.split('/src/')[1]:'布局或可复用 UI 组件：'+rel.split('/src/')[1];
 if(/\.(png|jpg|jpeg|ico|svg|mp4|gif|webp)$/.test(n)) return rel.includes('/sequence/')?'序列帧动画资源（单帧）':'静态图片、图标或演示视频资源';
 if(/\.(scss|css)$/.test(n)) return '全局或业务局部样式';
 if(ext==='.md') return '项目/模块说明或历史清理记录，以当前源码为准';
 if(ext==='.log') return '本地执行日志，不属于运行时源码';
 if(ext==='.sql') return '数据库 DDL/DML 脚本';
 if(rel.includes('/types/')||n.endsWith('.d.ts')) return 'TypeScript 类型或第三方模块声明';
 return '辅助文件；具体职责需结合文件内容复核';
}
const groups=[['前端','前端/smart-park-guat-web'],['后端','后端/smart-park-guat-server'],['根数据库','数据库']].map(([name,dir])=>({name,dir,files:walk(path.join(root,dir))}));
let lines=[
'# 前后端项目结构详解（供 GPT 审查）','',
'> 扫描日期：2026-10-17。基于当前磁盘工作区，包含未提交及未跟踪文件；不把 Git 已删除文件当作现存文件。静态结构梳理，未运行应用、构建或测试。','',
'## 1. 审查背景与范围','',
'这是一个智慧园区/机房管理项目，包含台账、三维机房、监测告警、能耗、网络拓扑、工单、账号权限及 AI 巡检助手。下文给出逐文件清单和从源码提取的依赖/入口，帮助审查职责、模块边界、可维护性及目录命名。','',
'主目录为 `前端/smart-park-guat-web`、`后端/smart-park-guat-server`、`数据库`。根目录还有 `.gitignore`、`.vscode`、`docs`、`简历.html`；其中简历与业务应用无直接结构关系。`docs/系统重新设计/00-现状调研` 已有历史审计报告，本清单以当前磁盘为准。','',
'排除 npm 依赖、Maven 编译产物、打包产物、IDE 元数据和测试结果目录；所有纳入范围的文件（包括每张序列帧）逐项列出。本地 vendor 中的 Three.js 和 OrbitControls 保留在清单中。文件用途说明为人工概括或按文件位置归类，源码标识和依赖由静态正则提取，不能替代完整语义分析。','',
'## 2. 技术与组织方式','',
'- 前端：Vue 3、TypeScript、Vite 7、Pinia、Vue Router、Element Plus、ECharts、GSAP；三维运行库在 scene/vendor 本地保存。部分业务仍使用 JavaScript。package.json 也声明 axios，主控制台请求封装使用 fetch。','- 后端：Spring Boot 2.6.13，Maven，Java 8 编译目标，MyBatis、JdbcTemplate、MySQL、Lombok。版本来自仓库配置，未查询外部最新版本。','- 路由：/home、/user 使用 Layout；/maintenance 独立；/login 登录；首页驾驶舱通过页面状态切换，并非每个驾驶舱均有独立 URL。','- 认证与 HTTP：router → services/console.ts → API；助手通过 agent-client.ts 单独处理 SSE。服务端由 ConsoleAccessFilter 处理访问过滤。','',
'## 3. 主要业务链路','',
'```text','main.ts → App.vue → router → layouts / views',
'home/index.vue → composables → stores/platform、stores/monitoring → api/platform → services/console.ts',
'stores → useHomeSceneData → platform-scene-adapter → RoomScene.vue → scene.ts → scene/vendor',
'InspectionAssistant → agent-* → AssistantAgentController → AssistantAgentService → 业务工具 / IntegrationService',
'Controller → InventoryService → InventoryMapper → 数据库',
'其他业务 Controller → Console/Monitoring/Integration 等 Service → JdbcTemplate / 外部系统',
'```','',
'## 4. 结构审查关注点（建议，不是已验证故障）','',
'1. ConsoleService、IntegrationService、AssistantAgentService 和 scene.ts 的体积与职责；结合后文行数及方法，判断是否需要按领域拆分。',
'2. service 目录同时包含过滤器、调度、数据迁移和领域服务，是否应区分 web/security、application、infrastructure 等职责。',
'3. MyBatis 与 JdbcTemplate 并存，SQL 和业务逻辑边界是否一致；DTO、Map 响应及前端类型是否形成统一契约。',
'4. 前端 Pinia 与 services 中的模块级响应式状态并存，认证、工单、监测各自由谁管理；API 封装与 SSE 封装的共性是否清晰。',
'5. home 同时承载驾驶舱、场景与助手，业务间通过 imports 和状态关联，是否能独立演进；shared 组件是否放在实际复用范围内。',
'6. 根数据库目录、后端 migrations、维护脚本和启动迁移的职责及执行顺序是否明确。',
'7. JS/TS 混合、本地 vendor、演示数据与正式数据的边界，以及大量序列帧对维护和打包的影响。',
'8. 测试文件不少，但目录数量不能证明覆盖有效；还应核对脚本入口、测试隔离、关键业务验收和 CI。','',
];
for(const g of groups){
 lines.push(`## ${g.name}逐文件清单`, '',`相对根目录：\`${g.dir}\`；共 ${g.files.length} 个文件。`,'');
 lines.push('目录层级（文件完整路径见逐项清单）：','','```text',g.dir+'/');
 const dirs=[...new Set(g.files.flatMap(f=>{
  let d=path.relative(path.join(root,g.dir),path.dirname(f)).replaceAll('\\','/');
  const a=[]; while(d && d!=='.'){a.push(d);d=path.posix.dirname(d);} return a;
 }))].sort();
 for(const d of dirs) lines.push('  '.repeat(d.split('/').length)+'└─ '+path.posix.basename(d)+'/');
 lines.push('```','');
 let prev='';
 for(const f of g.files){
  const rel=path.relative(root,f).replaceAll('\\','/'), sub=path.relative(path.join(root,g.dir),f).replaceAll('\\','/');
  const parent=path.posix.dirname(sub);
  if(parent!==prev){lines.push(`### ${parent}`,'');prev=parent;}
  const binary=/\.(png|jpg|jpeg|ico|svg|mp4|gif|webp)$/.test(f);
  let s=binary?'':fs.readFileSync(f,'utf8');
  const count=s?s.split(/\r?\n/).length:0;
  lines.push(`- \`${sub}\` — ${role(rel)}。${binary?`${fs.statSync(f).size} 字节。`:`约 ${count} 行。`}`);
  if(!binary && /\.(vue|ts|js|java|mjs)$/.test(f) && !rel.includes('/vendor/')){
   const imports=[...s.matchAll(/(?:\bfrom\s*|\bimport\s*\(\s*|\bimport\s*)['"]([^'"]+)['"]/g)].map(m=>m[1]).filter(v=>v.startsWith('.')||v.startsWith('@/'));
   const javaDeps=[...s.matchAll(/\b(?:private|protected)\s+(?:final\s+)?([A-Z]\w*(?:Service|Mapper|Template|Policy))\s+\w+/g)].map(m=>m[1]);
   const symbols=f.endsWith('.java')?[...s.matchAll(/\bpublic\s+(?:static\s+)?(?:final\s+)?[\w<>?,.\[\] ]+\s+(\w+)\s*\(/g)].map(m=>m[1]):[...s.matchAll(/\b(?:export\s+)?(?:async\s+)?function\s+(\w+)|\bexport\s+(?:const|class|interface|type)\s+(\w+)/g)].map(m=>m[1]||m[2]);
   const maps=[...s.matchAll(/@(?:Request|Get|Post|Put|Patch|Delete)Mapping\s*\(([^\r\n]*)/g)].map(m=>m[0]);
   if(imports.length) lines.push('  - 本地依赖：'+[...new Set(imports)].map(v=>'`'+v+'`').join('、')+'。');
   if(javaDeps.length) lines.push('  - 声明依赖：'+[...new Set(javaDeps)].join('、')+'。');
   if(symbols.length) lines.push('  - 公开方法/导出标识：'+[...new Set(symbols)].map(v=>'`'+v+'`').join('、')+'。');
   if(maps.length) lines.push('  - HTTP 映射：'+maps.map(v=>'`'+v.replaceAll('`','')+'`').join('；')+'。');
  }
 }
 lines.push('');
}
lines.push('## 排除目录记录','',...omitted.map(([p,r])=>`- \`${p.replaceAll('\\','/')}\`：${r}。`),'',
'## 可直接发给 GPT 的审查提示词','',
'```text','请根据这份当前项目结构文档，审查智慧园区/机房管理系统的前后端组织是否合理。',
'先归纳现有架构及业务边界，再检查目录分层、文件职责、超大文件、模块耦合、组件复用、状态管理、API 契约、数据库迁移和测试组织。',
'请把有源码结构证据的问题与需要进一步读取源码才能判断的问题分开。不要仅凭目录名断言代码有 bug，也不要把第三方 vendor 代码当作业务代码。',
'按优先级列出改进项，每项给出现有文件、具体原因、建议归属和最小迁移步骤。提供建议目录树，并给出主要旧文件到新位置的映射。',
'保留现有功能和技术栈，优先渐进重构。不要默认引入微服务、DDD 全套、额外状态库或重写三维引擎。',
'如果信息不足，请列出最需要继续查看的 5–10 个源码文件及原因。','```','');
fs.writeFileSync(output,lines.join('\n'),'utf8');
console.log(JSON.stringify({output,groups:groups.map(g=>({name:g.name,files:g.files.length})),lines:lines.length}));
