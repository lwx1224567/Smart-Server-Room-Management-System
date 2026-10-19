<template>
  <section class="console-notices-panel" aria-label="消息中心">
    <div class="tools">
      <el-radio-group v-model="filter"
        ><el-radio-button value="all">全部</el-radio-button
        ><el-radio-button value="unread">未读</el-radio-button
        ><el-radio-button value="read">已读</el-radio-button></el-radio-group
      ><el-button @click="load">刷新</el-button>
    </div>
    <el-button class="delete" :disabled="!notices.some((n) => n.isRead)" @click="deleteRead"
      >一键删除所有已读消息</el-button
    >
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="notice-list">
      <p v-if="loading" role="status">正在加载消息…</p>
      <el-empty v-else-if="!filtered.length" description="暂无消息" />
      <article v-for="notice in filtered" :key="notice.id" :class="{ unread: !notice.isRead }">
        <h3>{{ notice.title }} <el-tag v-if="!notice.isRead" size="small">未读</el-tag></h3>
        <p>{{ notice.content }}</p>
        <small>{{ new Date(notice.createdAt).toLocaleString('zh-CN') }}</small>
        <div>
          <el-button v-if="!notice.isRead" link @click="markRead(notice.id)">标记已读</el-button
          ><el-button v-if="notice.orderId" link @click="openOrder(notice)">查看工单</el-button>
        </div>
      </article>
    </div>
  </section>
</template>
<script setup lang="ts">
import './console-dialog-theme.css';
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { auth, consoleApi } from '@/services/console';
const props = defineProps<{ active: boolean }>(),
  emit = defineEmits<{ count: [number]; 'open-order': [] }>(),
  router = useRouter();
const notices = ref<any[]>([]),
  filter = ref('all'),
  error = ref(''),
  loading = ref(false);
const filtered = computed(() =>
  notices.value.filter(
    (n) => filter.value === 'all' || (filter.value === 'read' ? !!n.isRead : !n.isRead),
  ),
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    notices.value = await consoleApi('/notifications');
    emit('count', notices.value.filter((n) => !n.isRead).length);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
watch(
  () => props.active,
  (value) => {
    if (value) void load();
  },
  { immediate: true },
);
async function markRead(id: number) {
  try {
    await consoleApi(`/notifications/${id}/read`, 'PUT');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function deleteRead() {
  try {
    await consoleApi('/notifications/read', 'DELETE');
    await load();
    ElMessage.success('已读消息已删除');
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function openOrder(notice: any) {
  await markRead(notice.id);
  emit('open-order');
  await router.push(
    auth.user?.role === 'MAINTAINER'
      ? { path: '/maintenance', query: { panel: 'orders', order: notice.orderId } }
      : { path: '/home', query: { business: 'alarm', order: notice.orderId } },
  );
}
defineExpose({ load });
</script>
<style scoped>
.console-notices-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.notice-list {
  flex: 1;
  min-height: 0;
  max-height: 55vh;
  overflow: auto;
  padding-right: 6px;
  scrollbar-width: thin;
  scrollbar-color: #237da5 #092441;
}
.tools {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}
.delete {
  flex-shrink: 0;
  align-self: flex-start;
  margin: 16px 0;
}
article {
  padding: 16px;
  border: 1px solid #245276;
  background: rgba(12, 51, 87, 0.45);
  border-radius: 6px;
  margin-bottom: 12px;
}
article.unread {
  border-left: 3px solid #36c8ed;
  background: rgba(27, 105, 153, 0.2);
}
h3 {
  font-size: 16px;
  margin: 0;
}
small {
  color: #91abc7;
}
article div {
  margin-top: 10px;
}
</style>
