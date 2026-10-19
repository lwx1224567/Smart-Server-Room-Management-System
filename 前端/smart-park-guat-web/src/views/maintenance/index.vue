<template>
  <div class="maintenance-layout">
    <Top name="智慧机房管控系统" />
    <div class="maintenance-backend console-themed-surface">
      <aside class="maintenance-sidebar">
        <div class="brand">
          <span class="brand-icon">维</span>
          <div><strong>智慧机房</strong><small>维保人员后台</small></div>
        </div>
        <nav aria-label="维保后台导航">
          <button
            v-for="item in menus"
            :key="item.key"
            :class="{ active: panel === item.key }"
            :aria-current="panel === item.key ? 'page' : undefined"
            @click="navigate(item.key)"
          >
            <span>{{ item.label }}</span
            ><b v-if="item.key === 'orders' && available.length">{{ available.length }}</b>
          </button>
        </nav>
      </aside>
      <div class="maintenance-content">
        <main>
          <section v-if="panel === 'overview'" class="overview">
            <div class="welcome">
              <h1>{{ auth.user?.displayName }}，欢迎回来</h1>
              <p>领取机房运维工单，记录处理过程，提交后由小维复测。</p>
            </div>
            <p v-if="orderStorage.error" role="alert">{{ orderStorage.error }}</p>
            <div class="summary-grid">
              <button
                v-for="item in summaries"
                :key="item.label"
                @click="navigate('orders', item.scope, item.status)"
              >
                <span>{{ item.label }}</span
                ><strong>{{ item.count }}</strong
                ><small>{{ item.description }}</small>
              </button>
            </div>
            <div class="overview-columns">
              <article>
                <div class="section-heading">
                  <h2>我的当前任务</h2>
                  <button @click="navigate('orders', 'mine')">查看全部</button>
                </div>
                <p v-if="!currentTasks.length" class="empty">
                  暂无正在处理的任务，可到待领取工单中领取。
                </p>
                <button
                  class="task-row"
                  v-for="order in currentTasks.slice(0, 5)"
                  :key="order.id"
                  @click="openOrder(order.id)"
                >
                  <span
                    ><strong>{{ order.title }}</strong
                    ><small>{{ order.orderCode || order.id }} · {{ order.device }}</small></span
                  ><span class="status">{{ order.status }}</span>
                </button>
              </article>
              <article class="flow-guide">
                <h2>处理流程</h2>
                <ol>
                  <li><b>领取工单</b><span>从待领取列表选择工单，成为负责人。</span></li>
                  <li><b>现场处理</b><span>开始处理并填写具体措施及处理结果。</span></li>
                  <li><b>提交复测</b><span>提交记录后等待小维核验真实采集数据。</span></li>
                </ol>
                <p>缺少真实采集数据时保持待复测，不会自动归档。</p>
                <button @click="navigate('orders', 'available')">查看待领取工单</button>
              </article>
            </div>
          </section>
          <PersonalAccount v-else-if="panel === 'profile'" />
          <WorkOrderCenter
            v-else
            :key="panel"
            :initial-scope="panel === 'history' ? 'history' : String(route.query.scope || 'mine')"
            :initial-status="String(route.query.status || '')"
            :order-id="typeof route.query.order === 'string' ? route.query.order : ''"
          />
        </main>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import '../../components/console-dialog-theme.css';
import { computed, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth } from '@/services/console';
import { frontendOrders, loadOrders, orderStorage } from '@/services/work-orders';
import WorkOrderCenter from '@/components/work-orders/WorkOrderCenter.vue';

import PersonalAccount from './PersonalAccount.vue';
import Top from '@/layouts/components/top/index.vue';
const route = useRoute(),
  router = useRouter();
const menus = [
  { key: 'overview', label: '工作概览' },
  { key: 'orders', label: '工单中心' },
  { key: 'history', label: '处理记录' },
  { key: 'profile', label: '个人信息与密码' },
];
const panel = computed(() =>
  menus.some((item) => item.key === route.query.panel)
    ? String(route.query.panel)
    : route.query.order
      ? 'orders'
      : 'overview',
);
const mine = computed(() =>
    frontendOrders.filter((order) => order.ownerAccountId === auth.user?.id),
  ),
  available = computed(() => frontendOrders.filter((order) => order.status === '待受理'));
const currentTasks = computed(() => mine.value.filter((order) => order.status !== '已完成'));
const summaries = computed(() => [
  {
    label: '待领取工单',
    count: available.value.length,
    scope: 'available',
    description: '等待维保人员领取',
  },
  {
    label: '我的当前任务',
    count: currentTasks.value.length,
    scope: 'mine',
    description: '已接单、处理中及待复测',
  },
  {
    label: '待复测',
    count: mine.value.filter((order) => order.status === '待复测').length,
    scope: 'mine',
    status: '待复测',
    description: '处理记录已提交',
  },
  {
    label: '已完成',
    count: mine.value.filter((order) => order.status === '已完成').length,
    scope: 'history',
    status: '已完成',
    description: '已完成并归档的工单',
  },
]);
function navigate(panel: string, scope?: string, status?: string) {
  void router.push({
    path: '/maintenance',
    query: { panel, ...(scope ? { scope } : {}), ...(status ? { status } : {}) },
  });
}
function openOrder(id: string) {
  void router.push({ path: '/maintenance', query: { panel: 'orders', order: id } });
}
async function refresh() {
  await loadOrders();
}
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  void refresh();
  timer = setInterval(() => {
    if (!document.hidden) void refresh();
  }, 30000);
});
onBeforeUnmount(() => clearInterval(timer));
</script>
<style scoped>
.maintenance-backend {
  display: flex;
  min-height: 100vh;
  color: #c6e2ff;
  font-family: Arial, 'Microsoft YaHei', sans-serif;
}
.maintenance-sidebar {
  width: 224px;
  flex-shrink: 0;
  background: rgba(12, 51, 87, 0.45);
  border-right: 1px solid #245276;
  display: flex;
  flex-direction: column;
  padding: 24px 18px;
  position: sticky;
  top: 0;
  height: 100vh;
  box-sizing: border-box;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 10px 30px;
}
.brand-icon {
  background: #103c59;
  color: #fff;
  border-radius: 10px;
  padding: 11px;
  font-size: 20px;
}
.brand strong {
  font-size: 20px;
}
.brand small {
  display: block;
  color: #91abc7;
  margin-top: 6px;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
button {
  font: inherit;
  cursor: pointer;
  border: 1px solid #297ba9;
  background: #0c3357;
  color: #c6e2ff;
  border-radius: 6px;
  padding: 10px 14px;
}
nav button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  text-align: left;
  border-color: transparent;
  background: transparent;
}
nav button.active {
  background: #103c59;
  color: #6edff5;
  border-color: #36c8ed;
}
.maintenance-content {
  min-width: 0;
  flex: 1;
}
nav b {
  background: #1f9cbd;
  border-radius: 10px;
  font-size: 12px;
  padding: 2px 6px;
}
main {
  padding: 20px;
  min-width: 0;
}
.overview {
  padding: 8px;
}
.welcome h1 {
  font-size: 25px;
  margin: 0;
}
.welcome p {
  color: #91abc7;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin: 26px 0;
}
.summary-grid button {
  text-align: left;
  background: rgba(12, 51, 87, 0.45);
  padding: 22px;
}
.summary-grid strong {
  display: block;
  font-size: 34px;
  color: #36c8ed;
  margin: 14px 0;
}
.summary-grid small {
  color: #91abc7;
}
.overview-columns {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
}
.overview-columns article {
  background: rgba(12, 51, 87, 0.45);
  border: 1px solid #245276;
  border-radius: 8px;
  padding: 24px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.overview h2 {
  font-size: 18px;
  margin: 0;
}
.empty {
  padding: 36px 0;
  color: #91abc7;
}
.task-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 16px 0;
  border: 0;
  border-bottom: 1px solid #245276;
  background: transparent;
  text-align: left;
}
.task-row small {
  display: block;
  color: #91abc7;
  margin-top: 8px;
}
.status {
  font-size: 13px;
  color: #36c8ed;
  white-space: nowrap;
}
.flow-guide ol {
  padding-left: 20px;
}
.flow-guide li {
  padding: 10px;
}
.flow-guide li span {
  display: block;
  color: #91abc7;
  margin-top: 8px;
  line-height: 1.6;
}
.flow-guide p {
  font-size: 13px;
  color: #91abc7;
  line-height: 1.7;
}
button:hover {
  background: #123e60;
  border-color: #36c8ed;
  color: #65daf6;
}
button:focus-visible {
  outline: 2px solid #36c8ed;
  outline-offset: 3px;
}
h1,
h2,
.brand strong {
  color: #6edff5;
}
button:disabled {
  opacity: 0.5;
  cursor: wait;
}
@media (max-width: 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .overview-columns {
    grid-template-columns: 1fr;
  }
  .maintenance-sidebar {
    width: 190px;
  }
}
@media (max-width: 700px) {
  .maintenance-backend {
    display: block;
  }
  .maintenance-sidebar {
    width: auto;
    height: auto;
    position: static;
    padding: 16px;
  }
  .brand {
    padding-bottom: 15px;
  }
  nav {
    flex-direction: row;
    flex-wrap: wrap;
  }
  main {
    padding: 12px;
  }
}
</style>

<style scoped>
.maintenance-layout {
  width: 100%;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: url('@/assets/layouts/layout-bg.png') no-repeat center / 100% 100%;
}
.maintenance-layout .maintenance-backend {
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: transparent;
  overflow: hidden;
}
.maintenance-layout .maintenance-sidebar {
  position: static;
  height: 100%;
  overflow-y: auto;
  min-height: 0;
}
.maintenance-content {
  display: flex;
  min-height: 0;
}
main {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.overview {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.summary-grid button,
.overview-columns article {
  min-width: 0;
  overflow-wrap: anywhere;
}
.overview-columns {
  flex: 1;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
}
.section-heading {
  gap: 12px;
  flex-wrap: wrap;
}
.task-row > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}
@media (max-height: 800px) and (min-width: 1001px) {
  main {
    padding: 16px;
  }
  .summary-grid {
    margin: 18px 0;
    gap: 14px;
  }
  .summary-grid button {
    padding: 16px;
  }
  .summary-grid strong {
    margin: 10px 0;
    font-size: 30px;
  }
  .overview-columns article {
    padding: 18px;
  }
  .flow-guide li {
    padding: 8px;
  }
}
@media (max-width: 1200px) {
  .maintenance-layout :deep(.pageTop .title span) {
    font-size: 28px;
    letter-spacing: 1px;
  }
  .maintenance-layout :deep(.pageTop .left),
  .maintenance-layout :deep(.pageTop .right) {
    padding-top: 65px;
    gap: 10px;
  }
}
@media (max-width: 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .overview-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 700px) {
  .maintenance-layout .maintenance-backend {
    display: flex;
    flex-direction: column;
  }
  .maintenance-layout .maintenance-sidebar {
    height: auto;
    flex-shrink: 0;
    padding: 12px;
  }
  .brand {
    padding: 0 8px 10px;
  }
  nav {
    gap: 6px;
  }
  nav button {
    padding: 8px;
    font-size: 13px;
  }
  .maintenance-content {
    flex: 1;
  }
  .maintenance-layout :deep(.pageTop .title span) {
    font-size: clamp(18px, 4.5vw, 28px);
  }
  .maintenance-layout :deep(.pageTop .left) {
    font-size: 12px;
    gap: 6px;
    padding-left: 8px;
  }
  .maintenance-layout :deep(.pageTop .right) {
    padding-right: 8px;
  }
  .maintenance-layout :deep(.pageTop .weather) {
    display: none;
  }
  .summary-grid {
    gap: 10px;
  }
  .summary-grid button {
    padding: 14px;
  }
  .overview-columns article {
    padding: 16px;
  }
}
</style>
