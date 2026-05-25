import type { ProcessRequest, TaskStatus, SubtitleEntry } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

export { API_BASE_URL };

export async function submitTask(request: ProcessRequest): Promise<{ task_id: string }> {
  const response = await fetch(`${API_BASE_URL}/api/video/process`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  return response.json();
}

export async function getTaskStatus(taskId: string): Promise<TaskStatus> {
  const response = await fetch(`${API_BASE_URL}/api/video/${taskId}/status`);
  return response.json();
}

export function getSubtitleSrtUrl(taskId: string): string {
  return `${API_BASE_URL}/api/subtitle/${taskId}/srt`;
}

export function getRecognizedTxtUrl(taskId: string): string {
  return `${API_BASE_URL}/api/subtitle/${taskId}/txt`;
}

export function getBurnedVideoUrl(taskId: string): string {
  return `${API_BASE_URL}/api/video/${taskId}/burned`;
}

export async function saveSubtitle(taskId: string, subtitles: SubtitleEntry[]): Promise<void> {
  await fetch(`${API_BASE_URL}/api/subtitle/${taskId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(subtitles),
  });
}
