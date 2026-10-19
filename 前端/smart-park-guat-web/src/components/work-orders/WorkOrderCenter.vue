<template>
  <section class="work-orders">
    <header>
      <h1>维保工单</h1>
      <span>{{ auth.user?.displayName }} · {{ admin ? '管理员查看' : '维保人员' }}</span>
    </header>
    <p class="note">与小维共享后端工单 · 提交处理记录后等待同来源的新监测数据复测</p>
    <p v-if="orderStorage.error" role="alert">{{ orderStorage.error }}</p>
    <div class="counts">
      <article v-for="item in statuses" :key="item">
        <span>{{ item }}</span
        ><b>{{ visibleOrders.filter((order) => order.status === item).length }}</b>
      </article>
    </div>
    <div class="filters">
      <input v-model="keyword" aria-label="搜索工单" placeholder="工单编号 / 标题 / 机柜" /><select
        v-model="status"
        aria-label="筛选工单状态"
      >
        <option value="">全部状态</option>
        <option v-for="item in statuses" :key="item">{{ item }}</option></select
      ><select v-model="source" aria-label="筛选工单来源">
        <option value="">全部来源</option>
        <option v-for="item in sources" :key="item">{{ item }}</option></select
      ><select v-model="priority" aria-label="筛选工单优先级">
        <option value="">全部优先级</option>
        <option>紧急</option>
        <option>高</option>
        <option>普通</option></select
      ><button @click="resetFilters">重置</button><button @click="loadOrders">刷新</button
      ><button v-if="admin" @click="creating = true">新建工单</button>
    </div>
    <div v-if="!admin" class="filters tabs">
      <button :aria-pressed="scope === 'mine'" @click="scope = 'mine'">我的工单</button
      ><button :aria-pressed="scope === 'available'" @click="scope = 'available'">待领取工单</button
      ><button :aria-pressed="scope === 'history'" @click="scope = 'history'">处理记录</button
      ><button :aria-pressed="scope === 'all'" @click="scope = 'all'">全部可见工单</button>
    </div>
    <h2>
      {{
        admin
          ? '全部工单'
          : scope === 'mine'
            ? '我的工单'
            : scope === 'available'
              ? '待领取工单'
              : scope === 'history'
                ? '处理记录'
                : '全部可见工单'
      }}
    </h2>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>工单编号</th>
            <th>标题</th>
            <th>机柜</th>
            <th>优先级</th>
            <th>负责人</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in filtered" :key="order.id">
            <td>{{ order.orderCode || order.id }}</td>
            <td>{{ order.title }}</td>
            <td>{{ order.device }}</td>
            <td>{{ order.priority }}</td>
            <td>{{ order.owner }}</td>
            <td>{{ order.status }}</td>
            <td>{{ formatOrderTime(order.created) }}</td>
            <td><button @click="open(order)">详情</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="orderStorage.loading" role="status">正在加载工单…</p>
    <p v-else-if="!filtered.length" class="note">暂无符合条件的工单，可切换列表或刷新。</p>
    <component
      :is="admin ? 'section' : ElDrawer"
      v-if="selected"
      :class="{ 'inline-order': admin, 'console-themed-dialog': !admin }"
      :model-value="!!selected"
      title="工单详情"
      size="min(560px, 96vw)"
      append-to-body
      @close="selected = undefined"
    >
      <header v-if="admin">
        <h2>工单处理</h2>
        <button @click="selected = undefined">收起</button>
      </header>
      <template v-if="selected"
        ><div class="order-detail">
          <div class="detail-heading">
            <div>
              <span class="order-number">{{ selected.orderCode || selected.id }}</span>
              <h3>{{ selected.title }}</h3>
            </div>
            <span class="status-chip" :class="{ complete: selected.status === '已完成' }">{{
              selected.status
            }}</span>
          </div>
          <div class="detail-facts">
            <div>
              <span>机柜 / 位置</span><strong>{{ selected.device }}</strong>
            </div>
            <div>
              <span>负责人</span><strong>{{ selected.owner }}</strong>
            </div>
            <div>
              <span>优先级</span
              ><strong :class="{ urgent: selected.priority === '紧急' }">{{
                selected.priority || '普通'
              }}</strong>
            </div>
          </div>
          <p class="detail-meta">
            {{ selected.source ?? '告警转工单' }} · 创建于 {{ formatOrderTime(selected.created) }}
          </p>
          <div
            v-if="
              selected.retestNote || selected.status === '已完成' || selected.status === '待复测'
            "
            class="retest-result"
            :class="{ passed: selected.status === '已完成' }"
            role="status"
          >
            <span class="result-symbol" aria-hidden="true">{{
              selected.status === '已完成' ? '✓' : '◷'
            }}</span>
            <div>
              <h4>
                {{
                  selected.status === '已完成'
                    ? '复检通过，工单已完成'
                    : selected.status === '待复测'
                      ? '维修已提交，等待复检'
                      : selected.retestEvidence?.passed === false
                        ? '复检未通过，请继续处理'
                        : '复检状态'
                }}
              </h4>
              <p>{{ retestExplanation(selected.retestNote) }}</p>
              <div
                v-for="(item, index) in selected.retestEvidence?.evidence"
                :key="index"
                class="reading-summary"
              >
                <span>{{ metricLabel(item.metric) }}</span
                ><strong>{{ item.value }} {{ item.unit }}</strong
                ><small>采样于 {{ formatOrderTime(item.observedAt) }}</small>
              </div>
              <small v-if="selected.retestEvidence?.checkedAt"
                >复检时间：{{ formatOrderTime(selected.retestEvidence.checkedAt) }}</small
              >
            </div>
          </div>
          <div v-if="selected.cause || selected.process || selected.result" class="repair-summary">
            <article>
              <span>哪里坏了</span>
              <h4>故障原因</h4>
              <p>{{ selected.cause || '尚未填写' }}</p>
            </article>
            <article>
              <span>怎么修的</span>
              <h4>处理措施</h4>
              <p>{{ selected.process || '尚未填写' }}</p>
            </article>
            <article>
              <span>修好了吗</span>
              <h4>处理结果</h4>
              <p>{{ selected.result || '尚未填写' }}</p>
            </article>
          </div>
          <p v-if="selected.submittedAt" class="detail-meta">
            提交时间：{{ formatOrderTime(selected.submittedAt) }}
          </p>
          <details v-if="selected.note" class="detail-disclosure">
            <summary>查看处理建议</summary>
            <p>{{ selected.note }}</p>
          </details>
          <button
            v-if="admin && selected.roomId && (selected.cabinetId || selected.deviceId)"
            :disabled="busy"
            @click="showHistory"
          >
            查看此对象维修历史
          </button>
          <div v-if="historyVisible" aria-label="对象维修历史">
            <h3>已完成维修历史</h3>
            <p v-if="!history.length">暂无已完成的维修历史。</p>
            <article v-for="item in history" :key="item.id">
              <p>
                {{ item.orderCode || item.id }} · 真实数据 · {{ formatOrderTime(item.updatedAt || item.created) }} ·
                {{ item.owner }}
              </p>
              <p>
                原因：{{ item.cause || '未填写' }} · 措施：{{ item.process || '未填写' }} · 结果：{{
                  item.result || '未填写'
                }}
              </p>
              <p>复检：{{ item.retestNote }}</p>
            </article>
          </div>
          <div
            v-if="admin && ['待受理', '已接单', '处理中'].includes(selected.status)"
            class="assignment"
          >
            <label
              >指派维保人员<select v-model="assignee" aria-label="指派维保人员">
                <option value="">请选择人员</option>
                <option v-for="person in people" :key="person.id" :value="person.id">
                  {{ person.displayName }}（{{ person.userName }}）
                </option>
              </select></label
            >
            <button
              :disabled="busy || !assignee"
              @click="runAction('assign', { ownerAccountId: Number(assignee) })"
            >
              确认指派
            </button>
          </div>
          <button v-if="!admin && selected.status === '待受理'" :disabled="busy" @click="claim">
            领取工单</button
          ><button v-if="own && selected.status === '已接单'" :disabled="busy" @click="start">
            开始处理
          </button>
          <template v-if="own && selected.status === '处理中'"
            ><label>故障原因<textarea v-model="cause" aria-label="故障原因" rows="3" /></label
            ><label
              >处理过程及措施<textarea
                v-model="process"
                aria-label="处理过程及措施"
                rows="4"
              /></label
            ><label>处理结果<textarea v-model="result" aria-label="处理结果" rows="3" /></label
            ><button :disabled="busy" @click="runAction('save', { cause, process, result })">
              保存处理记录</button
            ><button :disabled="busy" @click="submit">已解决，提交小维复检</button></template
          >
          <button
            v-if="admin && selected.status === '待复测'"
            :disabled="busy"
            @click="runAction('retest')"
          >
            小维复测
          </button>
          <h4 class="progress-heading">处理进度</h4>
          <ol class="order-timeline">
            <li v-for="(step, index) in timeline" :key="index">
              <div>
                <strong>{{ step.title }}</strong
                ><time>{{ step.time }}</time>
              </div>
              <p v-if="step.actor">{{ step.actor }}</p>
            </li>
          </ol>
          <details class="detail-disclosure">
            <summary>查看详细记录</summary>
            <h4>完整操作记录</h4>
            <p v-for="(step, index) in selected.steps" :key="index">{{ readableStep(step) }}</p>
            <h4 v-if="selected.retestEvidence?.evidence?.length">最新复测证据</h4>
            <p v-for="(item, index) in selected.retestEvidence?.evidence" :key="index">
              {{ metricLabel(item.metric) }} {{ item.value }} {{ item.unit }} ·
              {{ formatOrderTime(item.observedAt) }} · {{ item.source }}
            </p>
            <h4 v-if="selected.retestChecks?.length">历次复检依据</h4>
            <article v-for="(check, index) in selected.retestChecks" :key="index">
              <p>{{ formatOrderTime(check.checkedAt) }} · {{ check.note }}</p>
              <p v-for="(point, i) in check.evidence" :key="i">
                {{ metricLabel(point.metric) }} {{ point.value }} {{ point.unit }} ·
                {{ formatOrderTime(point.observedAt) }} · {{ point.source }}
              </p>
            </article>
          </details>
        </div>
      </template>
    </component>
    <el-dialog v-model="creating" title="新建工单" width="520px" class="console-themed-dialog"
      ><label>标题<input v-model="draft.title" aria-label="工单标题" /></label
      ><label>设备 / 位置<input v-model="draft.device" aria-label="工单设备或位置" /></label
      ><label
        >来源<select v-model="draft.source">
          <option v-for="item in sources" :key="item">{{ item }}</option>
        </select></label
      ><label
        >优先级<select v-model="draft.priority">
          <option>紧急</option>
          <option>高</option>
          <option>普通</option>
        </select></label
      ><label>问题描述<textarea v-model="draft.note" rows="3" /></label
      ><template #footer
        ><button @click="creating = false">取消</button
        ><button @click="createManual">创建</button></template
      ></el-dialog
    >
  </section>
</template>
<script setup lang="ts">
import { computed, ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth, consoleApi, type ConsoleUser } from '@/services/console';
import { ElMessage, ElDrawer } from 'element-plus';
import { formatOrderTime, formatOrderStep } from '@/utils/order-time';
import { useMonitoringStore } from '@/stores/monitoring';
import {
  frontendOrders,
  orderStorage,
  submitFrontendOrder,
  loadOrders,
  orderAction,
  createOrder,
  type FrontendOrder,
} from '@/services/work-orders';
const props = withDefaults(
  defineProps<{ initialScope?: string; initialStatus?: string; orderId?: string }>(),
  { initialScope: 'mine', initialStatus: '', orderId: '' },
);
const route = useRoute(),
  router = useRouter(),
  admin = computed(() => auth.user?.role === 'ADMIN'),
  scope = ref(props.initialScope),
  busy = ref(false);
const own = computed(() => selected.value?.ownerAccountId === auth.user?.id);
const people = ref<ConsoleUser[]>([]),
  assignee = ref<number | string>(''),
  cause = ref('');
const statuses = ['待受理', '已接单', '处理中', '待复测', '已完成'];
const keyword = ref(''),
  status = ref(props.initialStatus),
  process = ref(''),
  result = ref('');
const sources = ['告警转工单', '人工报修', '巡检缺陷', '计划维保'];
const source = ref(''),
  priority = ref(''),
  creating = ref(false);
const draft = reactive({ title: '', device: '', source: '人工报修', priority: '普通', note: '' });
const selected = ref<FrontendOrder>();
const actionLabels: Record<string, string> = {
  assign: '指派人员',
  claim: '领取工单',
  start: '开始维修',
  save: '保存处理记录',
  submit: '提交维修结果',
  retest: '发起复检',
  'demo-retest': '发起演示复检',
};
function metricLabel(metric: string) {
  return (
    ({ temperature: '温度', humidity: '湿度', power: '功率' } as Record<string, string>)[metric] ||
    metric
  );
}
function retestExplanation(note?: string) {
  if (note === '监测数据满足恢复阈值与持续时间，已归档')
    return '监测数据已恢复正常，并持续满足恢复条件。';
  if (
    note === '未收到本次提交后的对应对象有效监测数据' ||
    note === '小维正在等待提交后的有效监测数据，将自动复检'
  )
    return '正在等待维修后的有效监测数据，收到后将自动复检。';
  return note;
}
function readableStep(step: string) {
  return formatOrderStep(step)
    .split(' · ')
    .map((part) => actionLabels[part] || part.replace(/^assign → /, '指派给 '))
    .join(' · ');
}
const timeline = computed(() => {
  const order = selected.value;
  if (!order) return [];
  const result = [{ title: '创建工单', time: formatOrderTime(order.created), actor: '' }];
  for (const step of order.steps) {
    const [stamp, actor = '', action = ''] = step.split(' · ');
    if (!stamp || Number.isNaN(new Date(stamp).getTime()) || action === 'processing-record')
      continue;
    const command = action.split(' → ')[0] || '';
    if (
      command === 'submit' ||
      command === 'assign' ||
      command === 'claim' ||
      command === 'start'
    ) {
      result.push({
        title:
          command === 'assign'
            ? `指派给 ${action.slice('assign → '.length)}`
            : actionLabels[command]!,
        time: formatOrderTime(stamp),
        actor,
      });
    } else if (actor.includes('复检') || command === 'retest' || command === 'demo-retest') {
      // 等待样本的重复检查保留在详细记录，进度只呈现有结论的检查。
      const check = order.retestChecks?.find(
        (item) =>
          item.checkedAt &&
          Math.abs(new Date(item.checkedAt).getTime() - new Date(stamp).getTime()) < 1000,
      );
      if (check?.passed === true || /已归档|复检通过/.test(action))
        result.push({ title: '复检通过，工单完成', time: formatOrderTime(stamp), actor });
      else if (check?.passed === false || /未通过/.test(action))
        result.push({ title: '复检未通过，继续维修', time: formatOrderTime(stamp), actor });
    } else
      result.push({
        title: actionLabels[action] || action || '更新工单',
        time: formatOrderTime(stamp),
        actor,
      });
  }
  if (order.status === '已完成' && !result.some((item) => item.title === '复检通过，工单完成'))
    result.push({
      title: '工单已完成',
      time: formatOrderTime(order.retestEvidence?.checkedAt || order.updatedAt),
      actor: '',
    });
  return result;
});
const history = ref<FrontendOrder[]>([]),
  historyVisible = ref(false);
async function showHistory() {
  const order = selected.value;
  if (!order?.roomId) return;
  busy.value = true;
  historyVisible.value = false;
  try {
    const params = new URLSearchParams({ roomId: order.roomId });
    if (order.deviceId) params.set('deviceId', order.deviceId);
    else if (order.cabinetId) params.set('cabinetId', order.cabinetId);
    const records = await consoleApi<FrontendOrder[]>('/work-orders/history?' + params);
    if (selected.value?.id === order.id) {
      history.value = records;
      historyVisible.value = true;
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
const visibleOrders = computed(() =>
  frontendOrders.filter(
    (order) =>
      admin.value ||
      scope.value === 'all' ||
      (scope.value === 'available'
        ? order.status === '待受理'
        : order.ownerAccountId === auth.user?.id &&
          (scope.value !== 'history' || !!order.process || order.status === '已完成')),
  ),
);
const filtered = computed(() =>
  visibleOrders.value.filter(
    (order) =>
      (!status.value || order.status === status.value) &&
      (!source.value || (order.source ?? '告警转工单') === source.value) &&
      (!priority.value || order.priority === priority.value) &&
      `${order.orderCode || ""} ${order.id} ${order.title} ${order.device}`
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase()),
  ),
);
function resetFilters() {
  keyword.value = '';
  status.value = '';
  source.value = '';
  priority.value = '';
}
async function createManual() {
  if (!draft.title.trim() || !draft.device.trim()) {
    ElMessage.warning('请填写标题和设备位置');
    return;
  }
  try {
    await createOrder({ ...draft, alarmEvent: 'manual', recordOrigin: 'MANUAL' });
    creating.value = false;
    Object.assign(draft, { title: '', device: '', source: '人工报修', priority: '普通', note: '' });
    resetFilters();
    ElMessage.success('工单已创建并保存');
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
function open(order: FrontendOrder) {
  historyVisible.value = false;
  selected.value = order;
  assignee.value = order.ownerAccountId ?? '';
  cause.value = order.cause ?? '';
  process.value = order.process ?? '';
  result.value = order.result ?? '';
}
async function runAction(action: string, body: Record<string, unknown> = {}) {
  if (!selected.value || busy.value) return;
  busy.value = true;
  try {
    await orderAction(selected.value, action, body);
    if (action === 'retest' || action === 'demo-retest') await useMonitoringStore().refresh();
    if (action === 'claim') scope.value = 'mine';
    ElMessage.success(
      action === 'retest' || action === 'demo-retest'
        ? (selected.value.retestNote ?? '已请求复测')
        : '工单已保存',
    );
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
function claim() {
  void runAction('claim');
}
function start() {
  void runAction('start');
}
async function submit() {
  if (!selected.value || busy.value) return;
  busy.value = true;
  try {
    await submitFrontendOrder(selected.value, process.value, result.value, cause.value);
    ElMessage.success('已提交处理记录，等待同来源的新监测数据复测');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
function selectQuery() {
  if (admin.value) return;
  const order = frontendOrders.find((o) => o.id === (props.orderId || route.query.order));
  if (order) open(order);
}
onMounted(async () => {
  await loadOrders();
  if (admin.value) {
    try {
      people.value = (await consoleApi<ConsoleUser[]>('/users')).filter((person) => person.enabled);
    } catch (e) {
      ElMessage.error((e as Error).message);
    }
  }
  selectQuery();
});
const refreshTimer = setInterval(() => {
  if (!busy.value) void loadOrders();
}, 15000);
onBeforeUnmount(() => clearInterval(refreshTimer));
watch(() => route.query.order, selectQuery);
watch(() => props.orderId, selectQuery);
watch(
  () => props.initialScope,
  (value) => {
    if (scope.value !== value) {
      scope.value = value;
      resetFilters();
      selected.value = undefined;
    }
  },
);
watch(
  () => props.initialStatus,
  (value) => {
    status.value = value;
  },
);
watch(scope, (value) => {
  if (!admin.value && route.path === '/maintenance' && route.query.panel !== 'history')
    void router.replace({ path: '/maintenance', query: { ...route.query, scope: value } });
});
</script>
<style scoped>
.inline-order {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #245276;
  border-radius: 6px;
  background: #092943;
}
.order-detail {
  color: #c6e2ff;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.detail-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin: 18px 0;
}
.order-number {
  font-size: 11px;
  color: #86a7c1;
  letter-spacing: 0.5px;
}
.detail-heading h3 {
  margin: 5px 0 0;
  font-size: 20px;
  color: #e0f2ff;
}
.status-chip {
  flex-shrink: 0;
  padding: 5px 12px;
  color: #70d8f4;
  background: #164862;
  border: 1px solid #2b7d9b;
  border-radius: 20px;
  font-size: 12px;
}
.status-chip.complete {
  color: #79e0c2;
  background: #103f44;
  border-color: #287568;
}
.detail-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  background: #0c335755;
  border: 1px solid #245276;
  padding: 14px 16px;
  border-radius: 8px;
}
.detail-facts span {
  display: block;
  color: #86a7c1;
  font-size: 12px;
  margin-bottom: 5px;
}
.detail-facts strong {
  font-size: 14px;
  font-weight: 500;
}
.detail-facts .urgent {
  color: #ffb483;
}
.detail-meta {
  font-size: 12px;
  color: #86a7c1;
  margin: 10px 0 18px;
}
.retest-result {
  display: flex;
  gap: 12px;
  padding: 18px;
  background: #123c59;
  border: 1px solid #2a6886;
  border-radius: 8px;
  margin: 18px 0;
}
.retest-result.passed {
  background: linear-gradient(115deg, #12433f99, #0d353e77);
  border-color: #287568;
}
.result-symbol {
  display: grid;
  place-items: center;
  align-self: flex-start;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #28748c55;
  color: #79dff6;
  font-size: 18px;
}
.passed .result-symbol {
  background: #28846c55;
  color: #79e0c2;
}
.retest-result h4 {
  color: #d5eeff;
  margin: 0;
  font-size: 15px;
}
.passed h4 {
  color: #8ae7cd;
}
.retest-result p {
  margin: 6px 0;
  color: #a6c7d6;
  font-size: 12px;
}
.retest-result small {
  color: #86a7b9;
  font-size: 11px;
}
.reading-summary {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  column-gap: 10px;
  margin: 10px 0;
}
.reading-summary strong {
  font-size: 20px;
  font-weight: 500;
  color: #d7fff3;
}
.reading-summary span {
  font-size: 12px;
  color: #a6c7d6;
}
.repair-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin: 18px 0 10px;
}
.repair-summary article {
  padding: 16px;
  background: #0c335755;
  border: 1px solid #245276;
  border-radius: 8px;
  min-width: 0;
}
.repair-summary article > span {
  font-size: 12px;
  color: #6ed4ee;
}
.repair-summary h4 {
  margin: 4px 0 10px;
  font-size: 12px;
  font-weight: 400;
  color: #86a7c1;
}
.repair-summary p {
  margin: 0;
  white-space: pre-wrap;
  color: #d0e7fa;
}
.progress-heading {
  margin: 24px 0 16px;
  font-size: 15px;
  color: #d5eeff;
}
.order-timeline {
  padding: 0;
  margin: 0 0 20px 5px;
  list-style: none;
}
.order-timeline li {
  position: relative;
  margin: 0;
  padding: 0 0 18px 22px;
  border-left: 1px solid #2c5e7e;
}
.order-timeline li:last-child {
  border-left-color: transparent;
  padding-bottom: 0;
}
.order-timeline li::before {
  content: '';
  position: absolute;
  top: 7px;
  left: -4px;
  width: 7px;
  height: 7px;
  background: #5acde7;
  border-radius: 50%;
  box-shadow: 0 0 0 4px #103b53;
}
.order-timeline li > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.order-timeline strong {
  font-size: 13px;
  font-weight: 500;
}
.order-timeline time,
.order-timeline p {
  font-size: 11px;
  color: #86a7c1;
}
.order-timeline p {
  margin: 3px 0 0;
}
.detail-disclosure {
  border-top: 1px solid #245276;
  padding: 13px 0;
}
.detail-disclosure summary {
  cursor: pointer;
  color: #89c7e3;
  font-size: 12px;
}
.detail-disclosure summary:focus-visible {
  outline: 2px solid #36c8ed;
  outline-offset: 4px;
}
.detail-disclosure p {
  color: #91abc7;
  font-size: 12px;
  white-space: pre-wrap;
}
.order-detail button {
  border-radius: 5px;
}
.assignment {
  margin: 16px 0;
  padding: 12px 16px;
  border: 1px solid #245276;
  border-radius: 8px;
}
.assignment select {
  display: block;
  margin-top: 8px;
  max-width: 100%;
}
@media (max-width: 600px) {
  .repair-summary {
    grid-template-columns: 1fr;
  }
  .detail-facts {
    gap: 8px;
    padding: 12px;
  }
  .detail-heading h3 {
    font-size: 18px;
  }
}
.work-orders {
  color: #c6e2ff;
  padding: 16px;
  font-size: 13px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
h1 {
  font-size: 22px;
  margin: 0;
}
h2,
h3 {
  font-size: 16px;
}
.note {
  color: #91abc7;
}
.counts {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 20px 0;
}
.counts article {
  border: 1px solid #245276;
  padding: 16px;
  background: #092943;
}
.counts b {
  display: block;
  font-size: 26px;
  color: #5cdcf3;
  margin-top: 8px;
}
.filters {
  display: flex;
  gap: 12px;
}
.table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid #245276;
}
button,
a {
  color: #b4e7ff;
  background: #0c3357;
  border: 1px solid #297ba9;
  padding: 7px 12px;
  cursor: pointer;
  text-decoration: none;
}
input,
select,
textarea {
  color: #c6e2ff;
  background: #082846;
  border: 1px solid #297ba9;
  padding: 8px;
}
label {
  display: block;
  margin: 12px 0;
}
textarea {
  display: block;
  width: 100%;
  box-sizing: border-box;
  margin-top: 7px;
}
@media (max-width: 700px) {
  .counts {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
<style scoped>
.filters {
  flex-wrap: wrap;
}
.tabs {
  margin-top: 16px;
}
.tabs button[aria-pressed='true'] {
  background: #103c59;
  border-color: #36c8ed;
  color: #6edff5;
}
.work-orders {
  max-width: 1400px;
  margin: auto;
}
button:disabled {
  opacity: 0.5;
  cursor: wait;
}
</style>
<style>
@import '../console-dialog-theme.css';
</style>
