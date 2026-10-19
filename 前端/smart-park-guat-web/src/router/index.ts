import { createRouter, createWebHistory } from 'vue-router';
import Layout from '@/layouts/index.vue';
import { auth, checkSession } from '@/services/console';
const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_BASE_URL),
  routes: [
    {
      path: '/',
      name: 'layout',
      component: Layout,
      redirect: '/home',
      children: [
        {
          path: '/home',
          name: 'home',
          component: () => import('@/views/home/index.vue'),
          meta: {
            title: '首页',
          },
        },
        {
          path: '/user',
          name: 'user',
          component: () => import('@/views/user/index.vue'),
          meta: {
            title: '用户管理',
          },
        },
      ],
    },
    {
      path: '/maintenance',
      name: 'maintenance',
      component: () => import('@/views/maintenance/index.vue'),
      meta: { title: '维保人员后台' },
    },
    {
      path: '/session-unavailable',
      name: 'session-unavailable',
      component: () => import('@/views/login/SessionUnavailable.vue'),
      meta: { title: '会话验证失败' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/index.vue'),
      meta: {
        title: '登录',
      },
    },
    { path: '/:pathMatch(.*)*', redirect: '/home' },
  ],
});

router.beforeEach(async (to) => {
  if (to.path === '/login' || to.name === 'session-unavailable') return true;
  try {
    if (!(await checkSession())) return { path: '/login' };
  } catch {
    return { name: 'session-unavailable', query: { redirect: to.fullPath }, replace: true };
  }
  if (auth.user?.role === 'MAINTAINER' && to.path !== '/maintenance')
    return { path: '/maintenance' };
  if (auth.user?.role === 'ADMIN' && to.path === '/maintenance') return { path: '/home' };
  return true;
});
window.addEventListener('console-session-expired', () => {
  void router.replace('/login');
});

export default router;
