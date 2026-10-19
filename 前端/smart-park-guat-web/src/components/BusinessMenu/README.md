# 底部业务菜单

素材来自参考项目 BusinessMenu，使用 PNG 图标、普通/高亮底座和整排背景。

```vue
<BusinessMenu v-model="activeKey" :items="menuItems" @select="handleSelect" />
```

- `items`：可选，数组项为 `{ key: string, name: string, icon: string }`，默认提供五个园区业务菜单。
- `v-model`：当前选中的菜单标识，初始空字符串表示没有选中；重复点击保持选中。
- `select`：点击后立即发出完整菜单项，可由页面处理路由跳转、面板切换或场景事件。
- 图标应通过 import 引入，以支持生产构建后的资源地址。

主布局已经接入默认菜单。底部独立占位，桌面宽度为页面的 50%，小屏扩大至 75% / 100%，避免遮挡主内容。组件本身不包含特定业务路由或事件总线，也不延迟点击；如业务请求需要防抖，可在页面的事件处理函数里添加。
