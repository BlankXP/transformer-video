import { useState, useEffect, useCallback } from "react";
import { listTasks, retryTask } from "../api/client";
import type { TaskStatus, TaskStage } from "../types";

const STAGE_LABELS: Record<string, string> = {
  downloading: "下载视频",
  extracting_audio: "提取音频",
  recognizing: "语音识别",
  translating: "翻译字幕",
  generating_subtitle: "生成字幕",
  burning_subtitle: "烧录字幕",
  completed: "已完成",
  failed: "失败",
};

function formatTime(ts?: number): string {
  if (!ts) return "-";
  return new Date(ts).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function getStageColor(stage: TaskStage | string): string {
  if (stage === "completed") return "text-green-600 bg-green-50";
  if (stage === "failed") return "text-red-600 bg-red-50";
  return "text-blue-600 bg-blue-50";
}

interface Props {
  authenticated: boolean;
  onTaskSelect: (taskId: string) => void;
  onRetry: (taskId: string) => void;
}

function TaskHistoryPanel({ authenticated, onTaskSelect, onRetry }: Props) {
  const [tasks, setTasks] = useState<TaskStatus[]>([]);
  const [loading, setLoading] = useState(false);
  const [retrying, setRetrying] = useState<string | null>(null);

  const fetchTasks = useCallback(async () => {
    if (!authenticated) return;
    setLoading(true);
    try {
      const data = await listTasks();
      setTasks(data);
    } catch {
      void 0;
    } finally {
      setLoading(false);
    }
  }, [authenticated]);

  useEffect(() => {
    fetchTasks();
    const interval = setInterval(fetchTasks, 10000);
    return () => clearInterval(interval);
  }, [fetchTasks]);

  const handleRetry = async (taskId: string) => {
    setRetrying(taskId);
    try {
      await retryTask(taskId);
      onRetry(taskId);
      await fetchTasks();
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : "重试失败");
    } finally {
      setRetrying(null);
    }
  };

  if (!authenticated) return null;

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-gray-700">历史任务</h3>
        <button
          onClick={fetchTasks}
          disabled={loading}
          className="text-xs text-blue-600 hover:text-blue-700 disabled:text-gray-400 transition"
        >
          {loading ? "加载中..." : "刷新"}
        </button>
      </div>
      {tasks.length === 0 ? (
        <p className="text-gray-400 text-sm text-center py-4">暂无历史任务</p>
      ) : (
        <div className="space-y-2 max-h-[400px] overflow-y-auto">
          {tasks.map((task) => (
            <div
              key={task.task_id}
              className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition"
            >
              <div
                className="flex-1 min-w-0 cursor-pointer"
                onClick={() => onTaskSelect(task.task_id)}
              >
                <div className="flex items-center gap-2">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${getStageColor(task.stage)}`}>
                    {STAGE_LABELS[task.stage] || task.stage}
                  </span>
                  <span className="text-xs text-gray-400">{formatTime(task.created_at)}</span>
                </div>
                <p className="text-sm text-gray-700 truncate mt-1">
                  {task.request?.url || task.task_id}
                </p>
                {task.stage === "failed" && task.message && (
                  <p className="text-xs text-red-500 truncate mt-0.5">{task.message}</p>
                )}
              </div>
              <div className="flex items-center gap-2 ml-3">
                {task.stage === "failed" && (
                  <button
                    onClick={() => handleRetry(task.task_id)}
                    disabled={retrying === task.task_id}
                    className="text-xs px-3 py-1.5 bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:bg-gray-300 transition whitespace-nowrap"
                  >
                    {retrying === task.task_id ? "重试中..." : "重试"}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default TaskHistoryPanel;
