import { normalizeDataSource } from '@/utils/unified-data-source';
import { reactive, ref } from 'vue';
import type { ApiResponse, HttpMethod } from '@/api/contracts';
import { ApiError } from '@/api/api-error';
export interface ConsoleUser {
  id: number;
  userName: string;
  displayName: string;
  role: 'ADMIN' | 'MAINTAINER';
  biography?: string;
  skills?: string[];
  phone?: string;
  email?: string;
  enabled: boolean;
}
const key = 'smart-room-session';
export const sessionCheckError = ref('');
export const auth = reactive<{ token: string; user?: ConsoleUser; checked: boolean }>({
  token: sessionStorage.getItem(key) ?? '',
  checked: false,
});
export function clearSession() {
  sessionStorage.removeItem(key);
  auth.token = '';
  auth.user = undefined;
  auth.checked = false;
  sessionCheckError.value = '';
  window.dispatchEvent(new Event('console-session-cleared'));
}
export async function consoleApi<T = any>(
  path: string,
  method: HttpMethod = 'GET',
  body?: unknown,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${import.meta.env.VITE_APP_BASE_API}/api/v1${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
  } catch (error) {
    const timeout = error instanceof Error && error.name === 'TimeoutError';
    throw new ApiError(timeout ? '请求超时，请重试' : '网络连接失败，请重试', {
      status: 0,
      type: timeout ? 'TIMEOUT_ERROR' : 'NETWORK_ERROR',
    });
  }
  // HTTP authentication failure remains definitive even if a proxy returns HTML.
  if (response.status === 401 && path !== '/auth/login') {
    clearSession();
    window.dispatchEvent(new Event('console-session-expired'));
  }
  let result: ApiResponse<T>;
  try {
    result = (await response.json()) as ApiResponse<T>;
    if (!result || typeof result !== 'object' || typeof result.code !== 'number')
      throw new Error('Invalid API envelope');
  } catch {
    throw new ApiError(
      response.status === 401 ? '登录已失效，请重新登录' : '服务响应异常，请重试',
      { status: response.status, type: response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR' },
    );
  }
  if (!response.ok || result.code !== 200) {
    throw new ApiError(result.msg || '请求失败，请重试', {
      status: response.status,
      code: result.code,
      type: result.error?.type || 'API_ERROR',
      fields: result.error?.fields,
      requestId: result.requestId,
      serverTime: result.serverTime,
    });
  }
  return normalizeDataSource(result.data) as T;
}
export async function signIn(userName: string, password: string) {
  const result = await consoleApi<{ token: string; user: ConsoleUser }>('/auth/login', 'POST', {
    userName,
    password,
  });
  auth.token = result.token;
  auth.user = result.user;
  auth.checked = true;
  sessionCheckError.value = '';
  sessionStorage.setItem(key, result.token);
  return result.user;
}
export async function checkSession() {
  if (!auth.token) return false;
  if (auth.checked && auth.user) return true;
  sessionCheckError.value = '';
  try {
    auth.user = await consoleApi<ConsoleUser>('/auth/me');
    auth.checked = true;
    return true;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return false;
    sessionCheckError.value = error instanceof Error ? error.message : '会话验证失败，请重试';
    throw error;
  }
}
export async function signOut() {
  await consoleApi('/auth/logout', 'POST');
  clearSession();
}
