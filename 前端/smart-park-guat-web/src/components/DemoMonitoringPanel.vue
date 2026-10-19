<template>
  <div class="demo-panel" v-loading="loading">
    <header class="panel-header">
      <div>
        <span class="eyebrow">监测数据管理</span>
        <h3>监测数据管理</h3>
        <p>支持 A-01 温度手动上报，与其他监测数据使用统一业务流程。</p>
      </div>
      <el-button :disabled="loading || submitting" @click="refresh">刷新状态</el-button>
    </header>
    <el-alert v-if="readError" :title="readError" type="error" :closable="false" show-icon />
    <template v-if="state">
      <section class="demo-card">
        <div class="card-title">
          <h4>手动温度上报</h4>
          <el-tag effect="plain">监测</el-tag>
        </div>
        <div class="identity">
          <strong>{{ state.cabinetName }}</strong
          ><span>{{ state.roomName }}</span>
        </div>
        <p class="muted">
          当前监测温度：{{ latest ? latest.value + ' ℃' : '暂无数据'
          }}{{ expired ? '（样本已过期）' : '' }}
        </p>
        <p class="muted">
          正常范围 {{ state.rule.min }}～{{ state.rule.max }} ℃ · 超过 {{ state.rule.max }} ℃
          触发高温告警
        </p>
        <div class="temperature-form">
          <label for="demo-temperature">上报温度（℃）</label>
          <el-input-number
            id="demo-temperature"
            v-model="temperature"
            :min="0"
            :max="100"
            :precision="1"
            :step="1"
            controls-position="right"
            :disabled="submitting || !!pending"
            aria-label="模拟温度"
          />
          <div class="quick-values">
            <el-button :disabled="submitting || !!pending" @click="temperature = 38"
              >填入 38℃</el-button
            ><el-button :disabled="submitting || !!pending" @click="temperature = 24"
              >填入 24℃</el-button
            >
          </div>
        </div>
        <p class="notice">
          确认后会向数据库新增监测记录，可能触发或更新告警。上报成功不代表工单完成。
        </p>
        <div class="actions">
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="
              !admin || loading || !!readError || state.rule.enabled !== true || !validTemperature
            "
            @click="submit"
            >{{ pending ? '重试上次上报' : '模拟上报' }}</el-button
          >
          <el-button v-if="pending" :disabled="submitting" @click="discardRetry"
            >开始新的上报</el-button
          >
        </div>
        <p v-if="pending" class="muted">
          上次请求未确认成功。重试将沿用原 requestId 和温度，避免重复新增样本。
        </p>
        <el-alert
          v-if="submitError"
          :title="submitError"
          type="error"
          :closable="false"
          show-icon
        />
        <div v-if="receipt" class="receipt" role="status">
          <strong>{{
            receipt.duplicate ? '已确认原上报记录（幂等重试）' : '监测 样本已保存'
          }}</strong
          ><span
            >样本 {{ receipt.readingId }} · {{ receipt.value }} ℃ ·
            {{ formatTime(receipt.observedAt) }}</span
          ><span v-if="receipt.requestCode">请求编号 {{ receipt.requestCode }}</span>
          <span v-if="receipt.batchCode">采集批次 {{ receipt.batchCode }}</span>
          <span>工单由后端根据维修提交后的样本自动复检，请刷新查看结果。</span>
        </div>
      </section>
      <section class="demo-card">
        <div class="card-title">
          <h4>监测与告警状态</h4>
          <span class="muted">手动刷新 · 无自动轮询</span>
        </div>
        <div class="status-grid">
          <div class="temperature-status">
            <span class="muted">最新监测温度</span
            ><strong>{{ latest ? latest.value + ' ℃' : '—' }}</strong
            ><el-tag :type="latest ? (normal(latest.value) ? 'success' : 'danger') : 'info'">{{
              latest ? (normal(latest.value) ? '正常' : '异常') : '无数据'
            }}</el-tag
            ><small>{{ latest ? formatTime(latest.observedAt) : '尚无受控监测温度样本' }}</small
            ><small v-if="expired" class="expired">样本已过期，不能作为当前复检证据。</small>
          </div>
          <div class="business-status">
            <span class="muted">当前监测高温告警</span>
            <p v-if="!activeAlarms.length">暂无活动告警</p>
            <div v-for="alarm in activeAlarms" :key="alarm.id" class="record-line">
              <el-tag type="danger">{{ alarm.status }}</el-tag
              ><span>{{ alarm.title || alarm.name || 'A-01 温度异常' }}</span
              ><small>{{ alarm.id }}</small>
            </div>
            <span class="muted">关联活动工单</span>
            <p v-if="!activeOrders.length">暂无活动工单</p>
            <div v-for="order in activeOrders" :key="order.id" class="record-line">
              <strong>{{ order.orderCode || order.id }}</strong
              ><el-tag>{{ order.status }}</el-tag
              ><span>{{ order.owner || '未指派' }}</span>
            </div>
          </div>
        </div>
        <div class="rule-line">
          规则：{{ state.rule.id }}
          <el-tag size="small" :type="state.rule.enabled === true ? 'success' : 'warning'">{{
            state.rule.enabled === true ? '已启用' : '未启用'
          }}</el-tag
          ><span>数据类型：真实数据 · 上报方式：手动</span>
        </div>
        <p class="muted">监测数据统一展示；已恢复告警、已完成工单不列为活动记录。</p>
      </section>
      <section class="demo-card">
        <div class="card-title">
          <h4>最近手动上报记录</h4>
          <span class="muted">最新 {{ records.length }} 条</span>
        </div>
        <el-table :data="records" max-height="260" empty-text="暂无监测温度上报记录">
          <el-table-column prop="requestCode" label="请求编号" min-width="160" />
          <el-table-column label="采集时间" min-width="164"
            ><template #default="{ row }">{{
              formatTime(row.observedAt)
            }}</template></el-table-column
          >
          <el-table-column label="机柜" width="70"
            ><template #default>A-01</template></el-table-column
          >
          <el-table-column label="温度" width="80"
            ><template #default="{ row }">{{ row.value }} ℃</template></el-table-column
          >
          <el-table-column label="温度状态" width="92"
            ><template #default="{ row }"
              ><el-tag size="small" :type="normal(row.value) ? 'success' : 'danger'">{{
                normal(row.value) ? '正常' : '异常'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="recordOrigin" label="来源" width="75" />
          <el-table-column prop="readingId" label="样本 ID" min-width="90" />
        </el-table>
      </section>
    </template>
    <el-empty v-else-if="!loading && !readError" description="暂无模拟监测配置" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessageBox } from 'element-plus';
import { auth } from '@/services/console';
import { useMonitoringStore } from '@/stores/monitoring';
import {
  demoMonitoringApi,
  demoReceipts,
  type DemoState,
  type TemperatureRequest,
  type TemperatureReceipt,
} from '@/api/platform/demo-monitoring';
const props = defineProps<{ active: boolean }>();
const monitoring = useMonitoringStore();
const state = ref<DemoState>(),
  temperature = ref<number | undefined>(38);
const loading = ref(false),
  submitting = ref(false),
  readError = ref(''),
  submitError = ref('');
const pending = ref<TemperatureRequest>(),
  receipt = ref<TemperatureReceipt>(),
  refreshedAt = ref(Date.now());
const admin = computed(() => auth.user?.role === 'ADMIN');
const validTemperature = computed(
  () =>
    typeof temperature.value === 'number' &&
    Number.isFinite(temperature.value) &&
    temperature.value >= 0 &&
    temperature.value <= 100,
);
const allRecords = computed(() => (state.value ? demoReceipts(state.value) : []));
const records = computed(() => allRecords.value.slice(0, 30)),
  latest = computed(() =>
    state.value?.snapshot.readings.find(
      (r) =>
        r.entityType === 'CABINET' &&
        r.entityId === state.value!.cabinetId &&
        r.metric === 'temperature' &&
        r.unit === '℃' &&
        r.quality === 'GOOD',
    ),
  );
const expired = computed(
  () =>
    latest.value &&
    state.value &&
    refreshedAt.value - Date.parse(latest.value.observedAt) >
      Number(state.value.rule.maxAgeSeconds) * 1000,
);
const ruleAlarms = computed(
  () =>
    state.value?.snapshot.alarms.filter(
      (a) =>
        a.recordOrigin === 'REAL' &&
        a.cabinetId === state.value!.cabinetId &&
        (a.metric === 'temperature' || /温度/.test(a.title || '')),
    ) ?? [],
);
const activeAlarms = computed(() => ruleAlarms.value.filter((a) => a.status !== '已恢复'));
const activeOrders = computed(
  () =>
    state.value?.orders.filter(
      (o) =>
        o.roomId === state.value!.roomId &&
        o.cabinetId === state.value!.cabinetId &&
        o.recordOrigin === 'REAL' &&
        o.status !== '已完成' &&
        ruleAlarms.value.some((a) => a.id === o.alarmEvent),
    ) ?? [],
);
function normal(value: number) {
  return (
    !!state.value && value >= Number(state.value.rule.min) && value <= Number(state.value.rule.max)
  );
}
function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '时间无效'
    : date.toLocaleString('zh-CN', { hour12: false });
}
async function refresh() {
  if (!admin.value || loading.value) return;
  loading.value = true;
  readError.value = '';
  try {
    const result = await demoMonitoringApi.read();
    if (admin.value) {
      state.value = result;
      refreshedAt.value = Date.now();
    }
  } catch (error) {
    readError.value = (error as Error).message;
  } finally {
    loading.value = false;
  }
}
watch(
  () => props.active,
  (active) => {
    if (active) void refresh();
  },
  { immediate: true },
);
async function discardRetry() {
  try {
    await ElMessageBox.confirm(
      '上次请求可能已经保存。建议先刷新核对记录；开始新上报会使用新的 requestId，可能新增一条记录。',
      '开始新的上报',
      { confirmButtonText: '开始新上报', cancelButtonText: '保留重试', type: 'warning' },
    );
    pending.value = undefined;
    submitError.value = '';
  } catch {
    /* User retained the original request. */
  }
}
async function submit() {
  if (
    !admin.value ||
    submitting.value ||
    !state.value ||
    !validTemperature.value ||
    loading.value ||
    readError.value ||
    state.value.rule.enabled !== true
  )
    return;
  submitting.value = true;
  try {
    const value = pending.value?.value ?? temperature.value!;
    await ElMessageBox.confirm(
      `将向开发数据库新增 A-01 的监测温度记录（${value} ℃），可能触发或更新告警。上报成功不代表工单完成。是否继续？`,
      '确认模拟上报',
      {
        type: 'warning',
        confirmButtonText: '确认上报',
        cancelButtonText: '取消',
        customClass: 'console-themed-dialog',
      },
    );
    if (!admin.value) return;
    pending.value ??= { cabinetId: state.value.cabinetId, value, requestId: crypto.randomUUID() };
    submitError.value = '';
    try {
      receipt.value = await demoMonitoringApi.submit(state.value.roomId, { ...pending.value });
      pending.value = undefined;
    } catch (error) {
      submitError.value = `${(error as Error).message}。可使用原请求重试，或先刷新核对是否已保存。`;
      return;
    }
    await Promise.all([refresh(), monitoring.refresh()]);
  } catch (error) {
    if (error instanceof Error) submitError.value = error.message;
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.demo-panel {
  min-width: 0;
  max-height: 70vh;
  overflow: auto;
  padding-right: 5px;
  color: #c6e2ff;
}
.panel-header,
.card-title,
.actions,
.quick-values {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.panel-header {
  margin-bottom: 18px;
  align-items: flex-start;
}
.eyebrow {
  font-size: 11px;
  color: #69dcf5;
  letter-spacing: 1.4px;
}
.panel-header h3 {
  margin: 6px 0;
  color: #e2f3ff;
  font-size: 20px;
}
.panel-header p,
.muted {
  color: #8aabc5;
  font-size: 12px;
  line-height: 1.7;
}
.panel-header p {
  margin: 0;
}
.demo-card {
  margin: 14px 0;
  padding: 18px;
  border: 1px solid #245276;
  border-radius: 8px;
  background: linear-gradient(125deg, rgba(12, 51, 87, 0.75), rgba(7, 31, 59, 0.9));
}
.card-title {
  margin-bottom: 14px;
}
.card-title h4 {
  margin: 0;
  color: #d7eeff;
  font-size: 15px;
}
.identity {
  display: flex;
  align-items: center;
  gap: 12px;
}
.identity strong {
  font-size: 24px;
  color: #6edff5;
}
.identity span {
  color: #b4d4ed;
}
.temperature-form {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.temperature-form label {
  font-size: 13px;
}
.quick-values {
  justify-content: flex-start;
}
.quick-values :deep(.el-button + .el-button) {
  margin-left: 0;
}
.notice {
  padding: 10px 12px;
  background: rgba(230, 162, 60, 0.09);
  border-left: 2px solid #c8a66e;
  color: #dbc6a5;
  font-size: 12px;
  line-height: 1.8;
}
.actions {
  justify-content: flex-start;
}
.receipt {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 12px;
  background: rgba(71, 190, 152, 0.08);
  border: 1px solid #327d71;
  border-radius: 6px;
  font-size: 12px;
}
.receipt strong {
  color: #8ae0c1;
}
.status-grid {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 20px;
}
.temperature-status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 9px;
  border-right: 1px solid #245276;
  padding-right: 18px;
}
.temperature-status strong {
  font-size: 32px;
  color: #e2f3ff;
  font-weight: 600;
}
.temperature-status small {
  color: #8aabc5;
  font-size: 11px;
}
.temperature-status .expired {
  color: #dbc6a5;
  line-height: 1.6;
}
.business-status p {
  font-size: 13px;
  color: #a0bdd2;
  margin: 8px 0 18px;
}
.record-line {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 8px 0 16px;
  font-size: 12px;
}
.record-line small {
  width: 100%;
  overflow-wrap: anywhere;
  color: #8aabc5;
}
.rule-line {
  font-size: 11px;
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #a0bdd2;
}
.demo-panel :deep(.el-alert) {
  margin-top: 10px;
}
.demo-panel :deep(.el-input-number) {
  width: 140px;
  --el-input-bg-color: #0c3357;
  --el-input-text-color: #d7eeff;
  --el-input-border-color: #297ba9;
}
.demo-panel :deep(.el-table) {
  font-size: 12px;
}
.demo-panel :deep(.el-table .cell) {
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
  .temperature-status {
    border: 0;
    border-bottom: 1px solid #245276;
    padding-bottom: 16px;
  }
  .panel-header {
    flex-wrap: wrap;
  }
  .demo-card {
    padding: 12px;
  }
}
</style>
