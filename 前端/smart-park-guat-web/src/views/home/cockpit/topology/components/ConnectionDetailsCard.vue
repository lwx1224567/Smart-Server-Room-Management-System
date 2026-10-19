<template>
  <div class="card-body connection-details">
    <template v-if="selectedLink"
      ><div class="row">
        <b>{{ selectedLink.from }} → {{ selectedLink.to }}</b>
      </div>
      <div class="row">
        <span>连接状态</span><b>{{ selectedLink.status ?? '未采集' }}</b>
      </div>
      <div v-if="selectedLink.memberCount > 1" class="row">
        <span>包含链路</span><b>{{ selectedLink.memberCount }} 条</b>
      </div>
      <small v-if="selectedLink.memberCount > 1">展开机柜可查看具体设备连接</small> </template
    ><template v-else-if="selectedNode"
      ><b class="detail-name">{{ selectedNode.name }} · {{ selectedNode.id }}</b>
      <div class="row">
        <span>所属机柜</span><b>{{ selectedNode.cabinetCode ?? '未关联机柜' }}</b>
      </div>
      <div class="row">
        <span>层级</span
        ><b>{{
          { core: '核心层', aggregate: '汇聚层', access: '接入层', terminal: '终端层' }[
            selectedNode.layer
          ] ?? '未登记'
        }}</b>
      </div>
      <div class="row">
        <span>运行状态</span><b>{{ selectedNode.status ?? '未采集' }}</b>
      </div>
      <button @click="emit('locate')">在三维机房定位</button></template
    >
    <p v-else class="empty">选择节点或连接查看详情</p>
    <small v-if="selectedLink || selectedNode">{{
      formatTime((selectedLink ?? selectedNode)?.observedAt)
    }}</small>
  </div>
</template>

<script setup>
defineProps(['selectedLink', 'selectedNode']);
const emit = defineEmits(['locate']);
function formatTime(value) {
  if (!value) return '采集时间未提供';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '采集时间未提供'
    : date.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false });
}
</script>

<style scoped>
.card-body.connection-details {
  flex: 1;
  height: auto;
  min-width: 0;
  min-height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0;
  overflow: visible;
  font-size: 12px;
  line-height: 18px;
}
.detail-name {
  flex-shrink: 0;
  overflow-wrap: anywhere;
  color: #d5f2ff;
}
.row {
  display: flex;
  flex: 1;
  min-height: 20px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0;
  border-bottom: 1px dashed #245276;
}
.row span {
  flex-shrink: 0;
}
.row b {
  text-align: right;
  overflow-wrap: anywhere;
}
small {
  flex-shrink: 0;
  color: #91abc7;
  font-size: 10px;
  line-height: 14px;
  overflow-wrap: anywhere;
}
button {
  flex-shrink: 0;
  padding: 4px 8px;
  border: 1px solid #297ba9;
  background: #0c3357;
  color: #b4e7ff;
  line-height: 18px;
  cursor: pointer;
}
.empty {
  margin: auto;
  padding: 0;
  text-align: center;
  color: #91abc7;
}
@media (max-height: 800px) {
  .connection-details {
    gap: 3px;
    line-height: 16px;
  }
  .row {
    min-height: 18px;
  }
  button {
    padding: 3px 8px;
    line-height: 16px;
  }
}
</style>
