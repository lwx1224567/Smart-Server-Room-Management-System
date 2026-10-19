import { expect, type Page } from '@playwright/test';
export async function mockConsole(page: Page, initialRole = 'ADMIN') {
  await page.route('**/api/v1/**', (route) =>
    route.fulfill({ json: { code: 200, data: { items: [], total: 0 } } }),
  );
  const users: any[] = [
    {
      id: 1,
      userName: 'admin_test',
      displayName: '管理员',
      role: 'ADMIN',
      enabled: true,
      password: 'AdminTest123!',
    },
    {
      id: 2,
      userName: 'maint_test',
      displayName: '维保甲',
      role: 'MAINTAINER',
      enabled: true,
      password: 'MaintTest123!',
    },
  ];
  const sessions = new Map<string, number>();
  const orders: any[] = [];
  const notices: any[] = [
    {
      id: 1,
      title: '新工单',
      content: '请处理设备告警',
      isRead: false,
      createdAt: '2026-10-09T08:00:00',
    },
    {
      id: 2,
      title: '系统消息',
      content: '已读信息',
      isRead: true,
      createdAt: '2026-10-09T07:00:00',
    },
  ];
  const publicUser = (u: any) => {
    const { password, ...rest } = u;
    return rest;
  };
  await page.route(
    '**/api/v1/{auth,users,work-orders,notifications,weather}{,/**}',
    async (route) => {
      const req = route.request(),
        path = new URL(req.url()).pathname.replace('/api/v1', ''),
        method = req.method(),
        body = req.postDataJSON() ?? {},
        token = (req.headers().authorization ?? '').replace('Bearer ', '');
      let user = users.find((u) => u.id === sessions.get(token)),
        data: any = null,
        status = 200,
        msg = 'success';
      if (path === '/auth/login') {
        user = users.find(
          (u) => u.userName === body.userName && u.password === body.password && u.enabled,
        );
        if (user) {
          const token = `test-token-${Date.now()}-${Math.random()}`;
          sessions.set(token, user.id);
          data = { token, user: publicUser(user) };
        } else {
          status = 401;
          msg = '账号或密码错误';
        }
      } else if (!user) {
        status = 401;
        msg = '请先登录';
      } else if (path === '/auth/me') data = publicUser(user);
      else if (path === '/auth/logout') sessions.delete(token);
      else if (path === '/weather')
        data = {
          city: '上海',
          available: true,
          temperature: 27,
          code: 2,
          updatedAt: '2026-10-09T11:00',
          provider: 'Open-Meteo',
        };
      else if (path === '/auth/password') {
        if (body.oldPassword !== user.password) {
          status = 400;
          msg = '原密码不正确';
        } else {
          user.password = body.newPassword;
          for (const [t, id] of sessions) if (id === user.id) sessions.delete(t);
        }
      } else if (path === '/users' && method === 'GET')
        data = users.filter((u) => u.role === 'MAINTAINER').map(publicUser);
      else if (path.startsWith('/users')) {
        const id = Number(path.split('/')[2]);
        if (method === 'POST') users.push({ ...body, id: users.length + 10, role: 'MAINTAINER' });
        else if (method === 'DELETE')
          users.splice(
            users.findIndex((u) => u.id === id),
            1,
          );
        else {
          const person = users.find((u) => u.id === id);
          Object.assign(person, { ...body, password: body.password || person.password });
        }
      } else if (path === '/notifications' && method === 'GET') data = notices;
      else if (path === '/notifications/read' && method === 'DELETE') {
        for (let i = notices.length - 1; i >= 0; i--) if (notices[i].isRead) notices.splice(i, 1);
      } else if (path.startsWith('/notifications/')) {
        const notice = notices.find((n) => n.id === Number(path.split('/')[2]));
        if (notice) notice.isRead = true;
      } else if (path === '/work-orders' && method === 'GET')
        data = orders.filter(
          (o) => user.role === 'ADMIN' || !o.ownerAccountId || o.ownerAccountId === user.id,
        );
      else if (path === '/work-orders' && method === 'POST') {
        data = orders.find(
          (o) =>
            body.alarmEvent &&
            body.alarmEvent !== 'manual' &&
            o.alarmEvent === body.alarmEvent &&
            o.device === body.device,
        );
        if (!data) {
          data = {
            ...body,
            id: `WO-TEST-${orders.length + 1}`,
            owner: '未指派',
            status: '待受理',
            created: new Date().toISOString(),
            steps: ['创建工单'],
            verified: false,
            siteChecked: false,
          };
          orders.unshift(data);
        }
      } else if (path.startsWith('/work-orders/')) {
        const [, , id, action] = path.split('/');
        data = orders.find((o) => o.id === id);
        if (action === 'assign') {
          const person = users.find(
            (u) => u.id === body.ownerAccountId && u.role === 'MAINTAINER' && u.enabled,
          );
          Object.assign(data, {
            ownerAccountId: person.id,
            owner: person.displayName,
            status: '已接单',
          });
        }
        if (action === 'claim') {
          data.ownerAccountId = user.id;
          data.owner = user.displayName;
          data.status = '已接单';
        }
        if (action === 'start') data.status = '处理中';
        if (action === 'submit') {
          Object.assign(data, body, {
            status: '待复测',
            siteChecked: true,
            retestNote: '等待真实数据复测',
          });
        }
        if (action === 'retest')
          data.retestNote = '真实监测数据接口未接入，无法核验；保持待复测，不自动归档。';
        data.steps.push(action);
      } else {
        status = 404;
        msg = '接口不存在';
      }
      await route.fulfill({ status, json: { code: status, msg, data } });
    },
  );
  const initial = users.find((u) => u.role === initialRole)!;
  sessions.set('initial-test-token', initial.id);
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('fixture-initialized')) {
      sessionStorage.setItem('smart-room-session', 'initial-test-token');
      sessionStorage.setItem('fixture-initialized', '1');
    }
  });
  return { users, sessions, orders, notices };
}
export async function loginAs(page: Page, role: 'ADMIN' | 'MAINTAINER') {
  await page.evaluate(() => sessionStorage.removeItem('smart-room-session'));
  await page.goto('/login');
  await page
    .getByRole('textbox', { name: '用户名', exact: true })
    .fill(role === 'ADMIN' ? 'admin_test' : 'maint_test');
  await page
    .getByRole('textbox', { name: '密码', exact: true })
    .fill(role === 'ADMIN' ? 'AdminTest123!' : 'MaintTest123!');
  await page.getByRole('button', { name: '登 录' }).click();
  await expect(page).toHaveURL(role === 'ADMIN' ? /\/home$/ : /\/maintenance$/);
}
