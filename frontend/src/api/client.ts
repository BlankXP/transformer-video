import type { ProcessRequest, TaskDetail, TaskSummary, SubtitleEntry } from '../types';

// 默认留空表示同源：生产由 nginx 把 /api 与 /ws 转发到后端，开发由 vite proxy 兜住。
// 仅在前端与后端分离部署（不同域名）时才需要设置 VITE_API_BASE_URL（构建期生效）。
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export { API_BASE_URL };

const TOKEN_KEY = 'bili_translator_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** 登录态失效，调用方应引导用户重新登录 */
export class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AuthError';
  }
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** 是否附带令牌，默认 true */
  auth?: boolean;
  /** 登录接口专用：401 时直接使用后端返回的错误文案 */
  isLogin?: boolean;
}

/**
 * 统一请求入口：所有非 2xx 都抛错，避免调用方把失败当成功。
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, isLogin = false } = options;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: auth ? authHeaders() : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error('无法连接到服务器，请检查网络后重试');
  }

  if (response.status === 401) {
    if (isLogin) {
      const data = await response.json().catch(() => ({}));
      throw new Error((data as { error?: string }).error || '用户名或密码错误');
    }
    removeToken();
    throw new AuthError('登录已过期，请重新登录');
  }

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const message = (data as { error?: string }).error;
    throw new Error(message || `请求失败（HTTP ${response.status}）`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function login(username: string, password: string): Promise<{ token: string; username: string }> {
  return request<{ token: string; username: string }>('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false,
    isLogin: true,
  });
}

export function submitTask(payload: ProcessRequest): Promise<{ task_id: string; already_exists?: boolean }> {
  return request<{ task_id: string; already_exists?: boolean }>('/api/video/process', {
    method: 'POST',
    body: payload,
  });
}

export function listTasks(): Promise<TaskSummary[]> {
  return request<TaskSummary[]>('/api/video/tasks');
}

export function retryTask(taskId: string): Promise<{ task_id: string }> {
  return request<{ task_id: string }>(`/api/video/${taskId}/retry`, { method: 'POST' });
}

export function deleteTask(taskId: string): Promise<void> {
  return request<void>(`/api/video/${taskId}`, { method: 'DELETE' });
}

export function getTaskStatus(taskId: string): Promise<TaskDetail> {
  return request<TaskDetail>(`/api/video/${taskId}/status`);
}

export type DownloadKind = 'srt' | 'txt' | 'burned' | 'video';

/**
 * 下载文件。
 *
 * <p>先用登录态换一张一次性票据（120 秒、用一次即失效），再让浏览器用 `<a href>`
 * 直链下载：文件由浏览器流式写盘，不会整个读进内存，JWT 也不会出现在 URL 里。
 */
export async function downloadFile(taskId: string, kind: DownloadKind, filename: string): Promise<void> {
  const { ticket } = await request<{ ticket: string; expires_in: number }>('/api/download-ticket', {
    method: 'POST',
    body: { task_id: taskId, kind },
  });

  const link = document.createElement('a');
  link.href = `${API_BASE_URL}/api/download/${ticket}`;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

export function saveSubtitle(taskId: string, subtitles: SubtitleEntry[]): Promise<{ message: string }> {
  return request<{ message: string }>(`/api/subtitle/${taskId}`, {
    method: 'PUT',
    body: subtitles,
  });
}
