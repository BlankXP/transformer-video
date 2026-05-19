import type { ProcessRequest, TaskStatus, SubtitleEntry } from '../types';

const API_BASE_URL = 'http://localhost:8000';

export async function submitTask(request: ProcessRequest): Promise<{ task_id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  return response.json();
}

export async function getTaskStatus(taskId: string): Promise<TaskStatus> {
  const response = await fetch(`${API_BASE_URL}/api/tasks/${taskId}`);
  return response.json();
}

export function getSubtitleSrtUrl(taskId: string): string {
  return `${API_BASE_URL}/api/tasks/${taskId}/subtitle`;
}

export async function saveSubtitle(taskId: string, subtitles: SubtitleEntry[]): Promise<void> {
  await fetch(`${API_BASE_URL}/api/tasks/${taskId}/subtitle`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ subtitles }),
  });
}
