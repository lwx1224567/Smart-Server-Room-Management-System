<template>
  <section class="people-entry">
    <h1>维保人员管理</h1>
    <p>通过管理员设置管理人员账号、联系方式和启用状态。</p>
    <el-button type="primary" @click="open = true">打开人员管理</el-button
    ><ConsoleSettings v-model="open" />
  </section>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import ConsoleSettings from '@/components/ConsoleSettings.vue';
const open = ref(true);
</script>
<style scoped>
.people-entry {
  padding: 24px;
  color: #c6e2ff;
}
</style>
