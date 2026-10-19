场景与巡检员源自 E:/Files/project1/运维机房/IDC运维源码-v49。Three.js 与 OrbitControls 保留 vendor 版本及 LICENSE.three；本轮不修改模型、材质、灯光、相机、布局或动画实现。

当前聊天入口为 agent-assistant.ts → agent-conversation.ts → agent-client.ts → 服务端 Agent。文字和语音转写统一交给服务端模型理解；前端不解析业务意图，不调用业务写入 API。

场景动作由 scene-action-executor.ts 校验后调用既有 RoomScene；主页明确定位和选择使用 home-scene-controls.ts。只控制三维模型，不代表物理设备状态。所有执行成功回执须核对实际场景状态。

业务写入由服务端准备 pending，用户核对确认卡片并点击按钮后提交 token、类型和 requestId；文字和语音确认不执行写入。回执中断后查询状态，禁止自动重提。数据来源沿用现有 DEMO/REAL 策略。

真实模型、HTTP/SSE、数据库、浏览器语音和 Three.js 动画仍需后续授权联调；模拟测试通过不等于真实联调通过。
