import { useState, useCallback } from "react";
import type { TaskDetail, ProcessRequest } from "../types";
import { submitTask, getTaskStatus } from "../api/client";

export default function useTask() {
  const [taskId, setTaskId] = useState<string | null>(null);
  const [status, setStatus] = useState<TaskDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startTask = useCallback(async (request: ProcessRequest) => {
    setLoading(true);
    setError(null);
    setStatus(null);
    try {
      const result = await submitTask(request);
      setTaskId(result.task_id);
      if (result.already_exists) {
        // 复用已有任务时把当前状态拉回来；失败也不影响任务本身
        try {
          const s = await getTaskStatus(result.task_id);
          setStatus(s);
        } catch (e) {
          console.warn("读取已有任务状态失败:", e);
        }
      }
      return result.task_id;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "任务提交失败");
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshStatus = useCallback(async () => {
    if (!taskId) return;
    try {
      const s = await getTaskStatus(taskId);
      setStatus(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : "任务状态读取失败");
    }
  }, [taskId]);

  const selectTask = useCallback(async (id: string) => {
    // 切换任务时先清空，避免上一个任务的字幕/视频残留
    setTaskId(id);
    setStatus(null);
    setError(null);
    try {
      const s = await getTaskStatus(id);
      setStatus(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : "任务状态读取失败");
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  return { taskId, status, loading, error, startTask, refreshStatus, selectTask, setTaskId, clearError };
}
