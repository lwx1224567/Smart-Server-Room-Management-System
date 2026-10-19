<template>
  <el-dialog
    v-model="visible"
    :title="auth.user?.role === 'ADMIN' ? '管理员设置' : '个人设置'"
    width="min(1180px, 94vw)"
    append-to-body
    align-center
    class="console-themed-dialog console-settings-dialog"
  >
    <div
      class="settings"
      :class="{
        'settings--fitted': tab === 'notices' || tab === 'profile',
        'settings--notices': tab === 'notices',
      }"
    >
      <nav aria-label="设置导航">
        <div class="nav-heading"><span>控制中心</span><small>系统与账号设置</small></div>
        <button
          :class="{ active: tab === 'notices' }"
          :aria-current="tab === 'notices' ? 'page' : undefined"
          @click="tab = 'notices'"
        >
          <el-icon><Bell /></el-icon>消息中心<el-badge :value="unread" :hidden="!unread" />
        </button>
        <button
          v-if="admin"
          :class="{ active: tab === 'demo' }"
          :aria-current="tab === 'demo' ? 'page' : undefined"
          @click="
            demoOpened = true;
            tab = 'demo';
          "
        >
          <el-icon><DataLine /></el-icon>监测数据管理
        </button>
        <button
          :class="{ active: tab === 'orders' }"
          :aria-current="tab === 'orders' ? 'page' : undefined"
          @click="tab = 'orders'"
        >
          <el-icon><Tickets /></el-icon>工单记录
        </button>
        <button
          v-if="admin"
          :class="{ active: tab === 'people' }"
          :aria-current="tab === 'people' ? 'page' : undefined"
          @click="showPeople"
        >
          <el-icon><User /></el-icon>人员管理</button
        ><button
          :class="{ active: tab === 'profile' }"
          :aria-current="tab === 'profile' ? 'page' : undefined"
          @click="tab = 'profile'"
        >
          <el-icon><Setting /></el-icon>
          {{ admin ? '管理员基本信息' : '个人信息' }}
        </button>
      </nav>
      <ConsoleNotices
        ref="notices"
        v-show="tab === 'notices'"
        :active="visible && tab === 'notices'"
        @count="emit('count', $event)"
        @open-order="visible = false"
      />
      <section v-if="tab === 'people' && admin" class="people-panel">
        <header class="section-heading">
          <div>
            <h3>人员管理</h3>
            <p>统一管理维保人员的账号、技能与使用状态</p>
          </div>
          <span class="section-badge">维保团队</span>
        </header>
        <div class="people-summary">
          <div>
            <span>人员总数</span><strong>{{ people.length }}<small> 人</small></strong>
          </div>
          <div>
            <span>已启用</span
            ><strong class="enabled-count"
              >{{ people.filter((person) => person.enabled).length }}<small> 人</small></strong
            >
          </div>
          <div>
            <span>已停用</span
            ><strong
              >{{ people.filter((person) => !person.enabled).length }}<small> 人</small></strong
            >
          </div>
        </div>
        <div class="toolbar">
          <input
            v-model="search"
            placeholder="搜索姓名 / 账号 / 电话"
            aria-label="搜索维保人员"
          /><el-button type="primary" :icon="Plus" @click="edit()">新增维保人员</el-button
          ><el-button :icon="Refresh" :loading="loading" @click="loadPeople">刷新</el-button>
        </div>
        <p v-if="error" role="alert">{{ error }}</p>
        <div class="list-caption">
          <span>维保人员列表</span
          ><small>{{
            search.trim() ? `搜索结果 ${filtered.length} 人` : `共 ${people.length} 人`
          }}</small>
        </div>
        <el-table
          :data="filtered"
          v-loading="loading"
          max-height="420"
          empty-text="暂无匹配的维保人员"
          ><el-table-column
            prop="userName"
            label="账号"
            min-width="120"
            show-overflow-tooltip
          /><el-table-column
            prop="displayName"
            label="姓名"
            min-width="100"
            show-overflow-tooltip
          /><el-table-column
            prop="biography"
            label="个人简介"
            min-width="140"
            show-overflow-tooltip
          /><el-table-column label="擅长技能" min-width="140" show-overflow-tooltip
            ><template #default="{ row }">{{
              row.skills?.join('、') || '未填写'
            }}</template></el-table-column
          ><el-table-column prop="phone" label="电话" min-width="130" /><el-table-column
            label="状态"
            width="90"
            ><template #default="{ row }"
              ><span class="person-status" :class="{ enabled: row.enabled }">{{
                row.enabled ? '启用' : '停用'
              }}</span></template
            ></el-table-column
          ><el-table-column label="操作" width="140"
            ><template #default="{ row }"
              ><el-button link @click="edit(row)">编辑</el-button
              ><el-button link type="danger" @click="remove(row)">删除</el-button></template
            ></el-table-column
          ></el-table
        >
        <p v-if="!loading && !people.length">暂无维保人员，请新增账号后切换角色验证。</p>
      </section>
      <div v-else-if="tab === 'orders'" class="orders-panel">
        <WorkOrderCenter initial-scope="all" />
      </div>
      <section v-else-if="tab === 'profile'" class="profile-panel">
        <header class="section-heading">
          <div>
            <h3>{{ admin ? '管理员基本信息' : '个人信息' }}</h3>
            <p>查看账号资料，管理登录密码</p>
          </div>
          <span class="section-badge">{{ admin ? '管理员' : '维保人员' }}</span>
        </header>
        <div class="profile-identity">
          <div class="profile-avatar">
            <el-icon><User /></el-icon>
          </div>
          <div>
            <h3>{{ auth.user?.displayName }}</h3>
            <span>{{ auth.user?.userName }}</span>
          </div>
        </div>
        <dl class="profile-details">
          <div>
            <dt>电话</dt>
            <dd>{{ auth.user?.phone || '未填写' }}</dd>
          </div>
          <div>
            <dt>邮箱</dt>
            <dd>{{ auth.user?.email || '未填写' }}</dd>
          </div>
          <div>
            <dt>个人简介</dt>
            <dd>{{ auth.user?.biography || '未填写' }}</dd>
          </div>
          <div>
            <dt>擅长技能</dt>
            <dd>{{ auth.user?.skills?.join('、') || '未填写' }}</dd>
          </div>
        </dl>
        <form class="password-card" @submit.prevent="changePassword">
          <h3>
            <el-icon><Lock /></el-icon>修改密码
          </h3>
          <p>更新密码后需要重新登录，请妥善保管新密码。</p>
          <div class="password-fields">
            <label
              >原密码<el-input
                v-model="password.oldPassword"
                type="password"
                show-password
                aria-label="原密码" /></label
            ><label
              >新密码<el-input
                v-model="password.newPassword"
                type="password"
                show-password
                aria-label="新密码"
                placeholder="8至128位" /></label
            ><label
              >确认新密码<el-input
                v-model="confirmation"
                type="password"
                show-password
                aria-label="确认新密码"
            /></label>
          </div>
          <el-button type="primary" native-type="submit" :loading="saving"
            >保存密码并重新登录</el-button
          >
        </form>
      </section>
      <div v-if="demoOpened && admin" v-show="tab === 'demo'" class="demo-settings-panel">
        <DemoMonitoringPanel :active="visible && tab === 'demo'" />
      </div>
    </div>
  </el-dialog>
  <el-dialog
    v-model="editing"
    :title="draft.id ? '编辑维保人员' : '新增维保人员'"
    width="min(520px, 94vw)"
    append-to-body
    align-center
    class="console-themed-dialog console-settings-dialog"
  >
    <el-form label-width="100px" @submit.prevent="save"
      ><el-form-item label="账号"
        ><el-input
          v-model="draft.userName"
          :disabled="!!draft.id"
          aria-label="维保账号"
          maxlength="100" /></el-form-item
      ><el-form-item label="姓名"
        ><el-input
          v-model="draft.displayName"
          aria-label="维保姓名"
          maxlength="100" /></el-form-item
      ><el-form-item :label="draft.id ? '重置密码' : '初始密码'"
        ><el-input
          v-model="draft.password"
          type="password"
          show-password
          aria-label="维保密码"
          :placeholder="draft.id ? '留空保留原密码' : '至少8位'" /></el-form-item
      ><el-form-item label="电话"
        ><el-input v-model="draft.phone" aria-label="维保电话" /></el-form-item
      ><el-form-item label="邮箱"
        ><el-input v-model="draft.email" aria-label="维保邮箱" /></el-form-item
      ><el-form-item label="个人简介"
        ><el-input
          v-model="draft.biography"
          type="textarea"
          maxlength="1000"
          aria-label="个人简介" /></el-form-item
      ><el-form-item label="擅长技能"
        ><el-select v-model="draft.skills" multiple aria-label="擅长技能"
          ><el-option
            v-for="tag in skillTags"
            :key="tag"
            :label="tag"
            :value="tag" /></el-select></el-form-item
      ><el-form-item label="启用账号"><el-switch v-model="draft.enabled" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="editing = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import './console-dialog-theme.css';
import { computed, reactive, ref, watch } from 'vue';
import {
  Bell,
  DataLine,
  Tickets,
  User,
  Setting,
  Plus,
  Refresh,
  Lock,
} from '@element-plus/icons-vue';
import WorkOrderCenter from '@/components/work-orders/WorkOrderCenter.vue';
import ConsoleNotices from '@/components/ConsoleNotices.vue';
import DemoMonitoringPanel from '@/components/DemoMonitoringPanel.vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { auth, consoleApi, clearSession, type ConsoleUser } from '@/services/console';
defineProps<{ unread?: number }>();
const emit = defineEmits<{ count: [number] }>();
const notices = ref<InstanceType<typeof ConsoleNotices>>();
async function loadNotices() {
  if (notices.value) return notices.value.load();
  try {
    const messages = await consoleApi<{ isRead: boolean }[]>('/notifications');
    emit('count', messages.filter((message) => !message.isRead).length);
  } catch {
    /* 消息面板打开时显示请求错误。 */
  }
}
const visible = defineModel<boolean>({ default: false });
const router = useRouter(),
  admin = computed(() => auth.user?.role === 'ADMIN');
const tab = ref('profile'),
  demoOpened = ref(false),
  people = ref<ConsoleUser[]>([]),
  search = ref(''),
  error = ref(''),
  loading = ref(false),
  saving = ref(false),
  editing = ref(false);
let openingOrders = false;
defineExpose({
  loadNotices,
  openOrders() {
    openingOrders = !visible.value;
    tab.value = 'orders';
    visible.value = true;
  },
});
const skillTags = ['制冷散热', '电气供电', '网络通信', '设备硬件'];
const empty = {
  id: 0,
  userName: '',
  displayName: '',
  password: '',
  phone: '',
  email: '',
  biography: '',
  skills: [] as string[],
  enabled: true,
};
const draft = reactive({ ...empty }),
  password = reactive({ oldPassword: '', newPassword: '' }),
  confirmation = ref('');
const filtered = computed(() =>
  people.value.filter((p) =>
    `${p.userName} ${p.displayName} ${p.phone}`
      .toLowerCase()
      .includes(search.value.trim().toLowerCase()),
  ),
);
watch(visible, (value) => {
  if (value) {
    tab.value = openingOrders ? 'orders' : admin.value ? 'people' : 'profile';
    openingOrders = false;
    password.oldPassword = '';
    password.newPassword = '';
    confirmation.value = '';
    if (admin.value) void loadPeople();
  }
});
function showPeople() {
  tab.value = 'people';
  void loadPeople();
}
async function loadPeople() {
  loading.value = true;
  error.value = '';
  try {
    people.value = await consoleApi('/users');
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function edit(person?: ConsoleUser) {
  Object.assign(draft, empty, person ?? {}, { password: '', skills: [...(person?.skills ?? [])] });
  editing.value = true;
}
async function save() {
  if (!draft.userName.trim() || !draft.displayName.trim())
    return ElMessage.warning('请填写账号和姓名');
  if ((!draft.id || draft.password) && draft.password.length < 8)
    return ElMessage.warning('密码至少8位');
  saving.value = true;
  try {
    await consoleApi(draft.id ? `/users/${draft.id}` : '/users', draft.id ? 'PUT' : 'POST', draft);
    editing.value = false;
    await loadPeople();
    ElMessage.success('人员信息已保存');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    saving.value = false;
  }
}
async function remove(person: ConsoleUser) {
  try {
    await ElMessageBox.confirm(
      `删除维保人员“${person.displayName}”？未完成工单将阻止删除。`,
      '删除人员',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
    await consoleApi(`/users/${person.id}`, 'DELETE');
    await loadPeople();
    ElMessage.success('已删除');
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
async function changePassword() {
  if (password.newPassword !== confirmation.value)
    return ElMessage.warning('两次输入的新密码不一致');
  if (password.newPassword.length < 8) return ElMessage.warning('密码至少8位');
  saving.value = true;
  try {
    await consoleApi('/auth/password', 'PUT', password);
    clearSession();
    visible.value = false;
    await router.replace('/login');
    ElMessage.success('密码已修改，请重新登录');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    saving.value = false;
  }
}
</script>
<style scoped>
.orders-panel {
  min-width: 0;
  background: #071f3b;
  border-radius: 6px;
  max-height: 70vh;
  overflow: auto;
}
.settings {
  display: grid;
  grid-template-columns: 196px minmax(0, 1fr);
  gap: 22px;
  min-height: 510px;
  max-height: 74vh;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #245276 transparent;
}
.settings nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
  border-right: 1px solid rgba(70, 153, 202, 0.22);
  padding-right: 16px;
}
.settings nav button,
.toolbar button {
  padding: 9px;
  border: 1px solid #297ba9;
  border-radius: 4px;
  cursor: pointer;
  background: #0c3357;
  color: #b4d4ed;
}
.settings nav button:hover,
.toolbar button:hover {
  background: #123e60;
  color: #65daf6;
}
.settings nav button.active {
  background: linear-gradient(90deg, rgba(29, 165, 208, 0.24), rgba(16, 60, 89, 0.5));
  border-color: #36c8ed;
  color: #6edff5;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.toolbar input {
  min-width: 0;
  flex: 1;
  padding: 8px;
  border: 1px solid #297ba9;
  border-radius: 4px;
  background: #0c3357;
  color: #c6e2ff;
}
.toolbar input::placeholder {
  color: #6d8fac;
}
.toolbar input:focus-visible,
.settings nav button:focus-visible,
.toolbar button:focus-visible {
  outline: 2px solid #36c8ed;
  outline-offset: 2px;
}
.settings section {
  min-width: 0;
  padding: 16px;
  border: 1px solid #245276;
  border-radius: 6px;
  background: rgba(12, 51, 87, 0.45);
}
.settings h3 {
  color: #6edff5;
}
.settings p[role='alert'] {
  color: #ff8c94;
}
.settings :deep(.el-table) {
  --el-table-bg-color: #0c3357;
  --el-table-tr-bg-color: #0c3357;
  --el-table-header-bg-color: #123e60;
  --el-table-row-hover-bg-color: #163e5a;
  --el-table-border-color: #245276;
  --el-table-text-color: #b4d4ed;
  --el-table-header-text-color: #c6e2ff;
}
.settings :deep(.el-loading-mask) {
  background: rgba(9, 36, 65, 0.85);
}
.settings label {
  display: block;
  margin: 12px 0;
  max-width: 380px;
}
.nav-heading {
  padding: 8px 12px 22px;
}
.nav-heading span {
  display: block;
  color: #d5efff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
}
.nav-heading small {
  display: block;
  color: #789bb7;
  margin-top: 8px;
  font-size: 12px;
}
.settings nav button {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  padding: 14px 12px;
  text-align: left;
  border-color: transparent;
  background: transparent;
  border-radius: 7px;
  font: inherit;
  font-size: 13px;
  transition:
    background 0.18s,
    border-color 0.18s;
}
.settings nav button.active::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 13px;
  bottom: 13px;
  width: 3px;
  background: #53d8f8;
  border-radius: 2px;
  box-shadow: 0 0 10px #36c8ed66;
}
.settings nav button .el-icon {
  font-size: 17px;
  flex-shrink: 0;
}
.settings nav :deep(.el-badge) {
  margin-left: auto;
}
.settings section {
  padding: 22px;
  border-color: rgba(70, 153, 202, 0.26);
  border-radius: 10px;
  background: linear-gradient(145deg, rgba(15, 52, 84, 0.68), rgba(7, 31, 59, 0.65));
}
.settings .section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}
.settings h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #d5efff;
}
.section-heading p,
.password-card > p {
  color: #89a9c2;
  font-size: 12px;
  line-height: 1.7;
  margin: 8px 0 0;
}
.section-badge {
  flex-shrink: 0;
  border: 1px solid #2a6989;
  border-radius: 5px;
  padding: 5px 9px;
  color: #71d7ef;
  font-size: 12px;
  background: #12506c40;
}
.people-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 22px;
}
.people-summary > div {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid #245276;
  border-radius: 8px;
  background: linear-gradient(120deg, #12416770, #0b294840);
}
.people-summary span {
  font-size: 12px;
  color: #91abc7;
}
.people-summary strong {
  font-size: 28px;
  font-weight: 600;
  color: #d5efff;
  font-variant-numeric: tabular-nums;
}
.people-summary strong.enabled-count {
  color: #6ee1ca;
}
.people-summary small {
  font-size: 12px;
  font-weight: 400;
  color: #89a9c2;
}
.toolbar {
  align-items: center;
  gap: 10px;
}
.toolbar .el-button + .el-button {
  margin-left: 0;
}
.toolbar input {
  padding: 10px 12px;
  border-color: #245276;
  border-radius: 6px;
  background: #071f3b;
  font: inherit;
  font-size: 13px;
}
.list-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 22px 0 12px;
  font-size: 13px;
}
.list-caption small {
  color: #89a9c2;
}
.settings :deep(.el-table) {
  border: 1px solid #245276;
  border-radius: 7px;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  font-size: 13px;
}
.settings :deep(.el-table th.el-table__cell) {
  height: 44px;
  font-weight: 500;
}
.settings :deep(.el-table td.el-table__cell) {
  padding: 14px 0;
}
.person-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #91abc7;
  font-size: 12px;
}
.person-status::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.person-status.enabled {
  color: #6ee1ca;
}
.profile-identity {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: #12416750;
  border: 1px solid #245276;
  border-radius: 8px;
}
.profile-avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: #71d7ef;
  background: #19628666;
  font-size: 26px;
  border: 1px solid #327895;
}
.profile-identity span {
  display: block;
  color: #91abc7;
  margin-top: 7px;
  font-size: 13px;
}
.profile-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
  margin: 24px 0;
}
.profile-details dt {
  color: #89a9c2;
  font-size: 12px;
  margin-bottom: 8px;
}
.profile-details dd {
  margin: 0;
  color: #c6e2ff;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.password-card {
  border-top: 1px solid #245276;
  padding-top: 22px;
}
.password-card h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}
.password-card h3 .el-icon {
  color: #6edff5;
}
.password-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 8px 0 10px;
}
.password-fields label {
  font-size: 12px;
  color: #91abc7;
  line-height: 2;
  min-width: 0;
}
.password-fields :deep(.el-input) {
  margin-top: 6px;
}
.demo-settings-panel,
.orders-panel {
  min-width: 0;
}
.settings > .console-notices-panel {
  min-width: 0;
}
.settings--fitted {
  min-height: 0;
  max-height: none;
  overflow: visible;
}
.settings--notices {
  height: min(74vh, calc(100dvh - 150px));
  grid-template-rows: minmax(0, 1fr);
}
.settings--notices > .console-notices-panel {
  min-height: 0;
  box-sizing: border-box;
}
.settings--notices :deep(.notice-list) {
  max-height: none;
}
.settings .profile-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  grid-template-rows: auto auto 1fr;
  gap: 16px 24px;
  align-content: start;
}
.profile-panel > .section-heading {
  grid-column: 1 / -1;
  margin-bottom: 0;
}
.profile-panel .profile-identity {
  grid-column: 1;
  grid-row: 2;
  padding: 14px;
}
.profile-panel .profile-details {
  grid-column: 1;
  grid-row: 3;
  grid-template-columns: 1fr;
  align-content: start;
  gap: 12px;
  margin: 0;
}
.profile-panel .password-card {
  grid-column: 2;
  grid-row: 2 / 4;
  border-top: 0;
  border-left: 1px solid #245276;
  padding: 0 0 0 24px;
}
.profile-panel .password-fields {
  grid-template-columns: 1fr;
  gap: 0;
}
.profile-panel .password-fields label {
  max-width: none;
  margin: 8px 0;
}
@media (max-width: 900px) {
  .settings {
    grid-template-columns: 166px minmax(0, 1fr);
    gap: 16px;
  }
  .settings section {
    padding: 16px;
  }
  .password-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
@media (max-width: 600px) {
  .settings .profile-panel {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }
  .profile-panel .profile-identity,
  .profile-panel .profile-details,
  .profile-panel .password-card {
    grid-column: 1;
    grid-row: auto;
  }
  .profile-panel .password-card {
    border-left: 0;
    border-top: 1px solid #245276;
    padding: 16px 0 0;
  }
  .settings {
    grid-template-columns: 1fr;
  }
  .settings nav {
    flex-direction: row;
    border: 0;
    overflow-x: auto;
    padding: 0 0 8px;
  }
  .nav-heading {
    display: none;
  }
  .settings nav button {
    white-space: nowrap;
    flex-shrink: 0;
    padding: 10px;
  }
  .settings {
    min-height: 0;
    gap: 12px;
  }
  .people-summary {
    gap: 8px;
  }
  .people-summary > div {
    padding: 12px;
  }
  .people-summary strong {
    font-size: 24px;
  }
  .profile-details {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .section-badge {
    display: none;
  }
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar input {
    flex-basis: 100%;
  }
}
</style>
