<template>
  <section class="personal-account">
    <h1>个人信息与账号设置</h1>
    <p class="hint">查看维保账号信息，修改密码后需要重新登录。</p>
    <div class="profile-grid">
      <article>
        <h2>基本信息</h2>
        <dl>
          <dt>姓名</dt>
          <dd>{{ auth.user?.displayName }}</dd>
          <dt>登录账号</dt>
          <dd>{{ auth.user?.userName }}</dd>
          <dt>角色</dt>
          <dd>维保人员</dd>
          <dt>电话</dt>
          <dd>{{ auth.user?.phone || '未填写' }}</dd>
          <dt>邮箱</dt>
          <dd>{{ auth.user?.email || '未填写' }}</dd>
        </dl>
        <p class="hint">联系方式和账号状态由管理员在人员管理中维护。</p>
      </article>
      <article>
        <h2>修改密码</h2>
        <form @submit.prevent="save">
          <label
            >原密码<el-input
              v-model="draft.oldPassword"
              aria-label="原密码"
              type="password"
              show-password
              autocomplete="current-password" /></label
          ><label
            >新密码<el-input
              v-model="draft.newPassword"
              aria-label="新密码"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="8至128位" /></label
          ><label
            >确认新密码<el-input
              v-model="confirmation"
              aria-label="确认新密码"
              type="password"
              show-password
              autocomplete="new-password" /></label
          ><el-button type="primary" native-type="submit" :loading="saving"
            >保存密码并重新登录</el-button
          >
        </form>
      </article>
    </div>
  </section>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { auth, consoleApi, clearSession } from '@/services/console';
const draft = reactive({ oldPassword: '', newPassword: '' }),
  confirmation = ref(''),
  saving = ref(false),
  router = useRouter();
async function save() {
  if (saving.value) return;
  if (!draft.oldPassword) return ElMessage.warning('请输入原密码');
  if (draft.newPassword.length < 8 || draft.newPassword.length > 128)
    return ElMessage.warning('新密码应为8至128位');
  if (draft.newPassword !== confirmation.value) return ElMessage.warning('两次输入的新密码不一致');
  saving.value = true;
  try {
    await consoleApi('/auth/password', 'PUT', draft);
    clearSession();
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
.personal-account {
  padding: 24px;
}
h1 {
  color: #6edff5;
  font-size: 24px;
  margin: 0 0 12px;
}
h2 {
  color: #6edff5;
  font-size: 17px;
}
.hint {
  color: #91abc7;
  line-height: 1.7;
}
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-top: 24px;
}
article {
  padding: 24px;
  background: rgba(12, 51, 87, 0.45);
  border: 1px solid #245276;
  border-radius: 10px;
}
dl {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 22px;
}
dt {
  color: #91abc7;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
label {
  display: block;
  margin-bottom: 20px;
}
.el-input {
  margin-top: 8px;
}
@media (max-width: 850px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
