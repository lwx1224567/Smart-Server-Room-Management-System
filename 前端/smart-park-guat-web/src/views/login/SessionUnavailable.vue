<template>
  <main class="session-unavailable">
    <section aria-labelledby="session-error-title">
      <h1 id="session-error-title">暂时无法验证登录状态</h1>
      <p role="alert">{{ sessionCheckError || '请检查网络连接，然后重试。' }}</p>
      <p>验证成功后将继续访问原页面。</p>
      <el-button type="primary" :loading="retrying" @click="retry">重新验证</el-button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { sessionCheckError } from '@/services/console';

const route = useRoute();
const router = useRouter();
const retrying = ref(false);
async function retry() {
  if (retrying.value) return;
  const redirect = route.query.redirect;
  const target =
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('//') &&
    !redirect.startsWith('/session-unavailable')
      ? redirect
      : '/home';
  retrying.value = true;
  try {
    await router.replace(target);
  } finally {
    retrying.value = false;
  }
}
</script>

<style scoped>
.session-unavailable {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #0d172b;
  color: #f3f6fc;
}
section {
  width: min(100%, 520px);
  padding: 32px;
  border: 1px solid #35445e;
  border-radius: 12px;
  background: #17253c;
}
h1 {
  font-size: 22px;
}
p {
  margin: 16px 0;
  line-height: 1.6;
}
</style>
