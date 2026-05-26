import { useState, useCallback } from "react";
import type { TaskStatus, ProcessRequest } from "../types";
import { submitTask, getTaskStatus } from "../api/client";

export default function useTask() {
  const [taskId, setTaskId] = useState<string | null>(null);
  const [status, setStatus] = useState<TaskStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startTask = useCallback(async (request: ProcessRequest) => {
    setLoading(true);
    setError(null);
    try {
      const result = await submitTask(request);
      setTaskId(result.task_id);
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
    } catch {
      void 0;
    }
  }, [taskId]);

  const selectTask = useCallback(async (id: string) => {
    setTaskId(id);
    try {
      const s = await getTaskStatus(id);
      setStatus(s);
    } catch {
      void 0;
    }
  }, []);

  return { taskId, status, loading, error, startTask, refreshStatus, selectTask, setTaskId };
}
