# 三维机房实现说明

三维机房使用 Vue 3 管理生命周期和业务数据，Three.js r160 通过 WebGL 绘制，GSAP 负责镜头和设备抽取的过渡动画。机房与设备由代码中的几何体和 Canvas 纹理生成。

## 文件职责

| 文件                                          | 负责什么                                                                           |
| --------------------------------------------- | ---------------------------------------------------------------------------------- |
| `RoomScene.vue`                               | 页面入口、挂载与销毁、选择状态、Vue 事件、监测数据监听、对外暴露操作               |
| `scene.ts`                                    | 渲染器、相机、灯光、机房和机柜建模、射线拾取、检视、柜门、设备抽取、动画与资源释放 |
| `types.ts`                                    | 场景机柜、分区、台账设备、控制与告警的输入类型                                     |
| `patrol.ts`                                   | 用几何体组装巡检员，沿通道移动，处理前往、到达、取消和恢复巡检                     |
| `icons.ts`                                    | 场景标签使用的 SVG 图标                                                            |
| `platform-scene-adapter.ts`                   | 把后台台账转换成场景模型数据，保留数据库身份与模型身份的映射                       |
| `cabinet-monitoring.ts`、`zone-monitoring.ts` | 同步监测数据与分区读数                                                             |
| `cabinetState.ts`                             | 演示模式的柜门控制和设备告警状态                                                   |
| `cameras.ts`、`facilities.ts`                 | 摄像头和附属设施的位置、名称等配置                                                 |
| `vendor/`                                     | 第三方 Three.js 和 OrbitControls 原始 JS，以及对应类型声明                         |

## 数据怎样进入画面

正式台账经 `stores/platform → useHomeSceneData → platform-scene-adapter → RoomScene.vue → createRoom()` 生成模型。监测数据经 monitoring store 和同步函数更新机柜告警、分区读数与控制状态。场景输入不存在时，保留原有演示数据路径。

`RoomScene.vue` 在挂载时调用 `createRoom()`，监听输入变化调用 `setMonitoring()`、`setInventory()` 等方法；销毁时调用 `dispose()`。

## 模型和交互怎样实现

- 地板、墙体、机柜框架、服务器、柜门等由 BoxGeometry、PlaneGeometry 等基础几何体组合；面板和格栅由 Canvas 绘制纹理。
- 机柜、柜门、巡检人物使用 Group 组织层级；柜门绕 doorPivot 旋转，人物手脚绕各自父节点摆动。
- 同类重复零件使用 InstancedMesh，箱体几何体通过缓存复用。
- 鼠标位置转换成标准坐标，由 Raycaster 找到命中的模型，再沿父节点查找模型身份，通知 Vue。
- 机柜检视克隆当前机柜并切换显示与相机参数，叠加台账设备模型；GSAP 控制设备前移和放回。
- requestAnimationFrame 驱动巡检、柜门、风扇、告警呼吸和画面渲染；页面隐藏时跳过渲染，并支持减少动态效果。
- 分区和巡检标签是叠加的 HTML 元素，通过三维坐标投影计算屏幕位置。
- ResizeObserver 处理容器尺寸变化；dispose 取消动画、事件和观察器，释放几何体、材质、纹理与 WebGL 上下文。

## TypeScript 边界

场景自有实现使用 TypeScript，公开方法类型从实现推导，输入契约放在 types.ts。第三方库继续使用官方 JavaScript，由与 r160 匹配的 @types/three 提供编译期类型；浏览器最终运行的仍是构建后的 JavaScript。本次迁移范围不包含助手服务和资产页面等其他目录中的 JavaScript。
