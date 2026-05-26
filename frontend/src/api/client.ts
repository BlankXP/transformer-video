import type { ProcessRequest, TaskStatus, SubtitleEntry } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

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

function authHeaders(): Record<string, string> {
  const token = getToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

export async function login(username: string, password: string): Promise<{ token: string; username: string }> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || '登录失败');
  }
  return response.json();
}

export async function submitTask(request: ProcessRequest): Promise<{ task_id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/video/process`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(request),
  });
  if (response.status === 401) {
    removeToken();
    window.location.reload();
    throw new Error('登录已过期');
  }
  return response.json();
}

export async function getTaskStatus(taskId: string): Promise<TaskStatus> {
  const response = await fetch(`${API_BASE_URL}/api/video/${taskId}/status`, {
    headers: authHeaders(),
  });
  if (response.status === 401) {
    removeToken();
    window.location.reload();
    throw new Error('登录已过期');
  }
  return response.json();
}

export function getSubtitleSrtUrl(taskId: string): string {
  const token = getToken();
  return `${API_BASE_URL}/api/subtitle/${taskId}/srt?token=${token || ''}`;
}

export function getRecognizedTxtUrl(taskId: string): string {
  const token = getToken();
  return `${API_BASE_URL}/api/subtitle/${taskId}/txt?token=${token || ''}`;
}

export function getBurnedVideoUrl(taskId: string): string {
  const token = getToken();
  return `${API_BASE_URL}/api/video/${taskId}/burned?token=${token || ''}`;
}

export async function saveSubtitle(taskId: string, subtitles: SubtitleEntry[]): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/subtitle/${taskId}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(subtitles),
  });
  if (response.status === 401) {
    removeToken();
    window.location.reload();
    throw new Error('登录已过期');
  }
}
