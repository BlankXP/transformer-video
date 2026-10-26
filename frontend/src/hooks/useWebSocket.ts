import { useState, useEffect, useRef } from "react";
import type { TaskStage } from "../types";
import { AuthError, getTaskStatus, getToken } from "../api/client";

interface WebSocketMessage {
  task_id: string;
  stage: TaskStage;
  progress: number;
  message: string;
}

/** 连续重连失败达到该次数后，降级为轮询 */
const MAX_RETRIES = 5;
const MAX_BACKOFF_MS = 15000;
const POLL_INTERVAL_MS = 3000;

const TERMINAL_STAGES: TaskStage[] = ["completed", "failed"];

export default function useWebSocket(taskId: string | null) {
  const [stage, setStage] = useState<TaskStage | null>(null);
  const [progress, setProgress] = useState<number>(0);
  const [message, setMessage] = useState<string>("");
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!taskId) {
      setStage(null);
      setProgress(0);
      setMessage("");
      setConnected(false);
      return;
    }

    let closed = false;
    let terminal = false;
    let retries = 0;
    let reconnectTimer: number | undefined;
    let pollTimer: number | undefined;

    const stopPolling = () => {
      if (pollTimer !== undefined) {
        window.clearInterval(pollTimer);
        pollTimer = undefined;
      }
    };

    // WebSocket 持续不可用时退化为轮询，保证进度不会永久停住
    const startPolling = () => {
      if (pollTimer !== undefined) return;
      console.warn("WebSocket 重连失败，降级为轮询任务状态");
      pollTimer = window.setInterval(async () => {
        try {
          const status = await getTaskStatus(taskId);
          setStage(status.stage);
          setProgress(status.progress);
          setMessage(status.message);
          if (TERMINAL_STAGES.includes(status.stage)) {
            stopPolling();
          }
        } catch (e) {
          // 登录过期时停止轮询：否则每 3 秒一次 401 会无限空转，用户也不知道该重新登录
          if (e instanceof AuthError) {
            stopPolling();
            return;
          }
          // 其它错误（网络抖动等）忽略，下一轮继续
        }
      }, POLL_INTERVAL_MS);
    };

    const connect = () => {
      const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      // 浏览器 WebSocket 无法设置请求头，令牌走查询参数，由后端握手拦截器校验
      const token = getToken();
      const query = token ? `?token=${encodeURIComponent(token)}` : "";
      const ws = new WebSocket(`${wsProtocol}//${window.location.host}/ws/${taskId}${query}`);
      wsRef.current = ws;

      ws.onopen = () => {
        retries = 0;
        setConnected(true);
        stopPolling();
      };

      ws.onmessage = (event) => {
        try {
          const data: WebSocketMessage = JSON.parse(event.data);
          setStage(data.stage);
          setProgress(data.progress);
          setMessage(data.message);
          if (TERMINAL_STAGES.includes(data.stage)) {
            // 终态后服务端不再推送，主动断开，避免空挂到代理超时再触发无谓重连
            terminal = true;
            ws.close();
          }
        } catch {
          // 忽略无法解析的消息
        }
      };

      // 统一由 onclose 处理重连，避免 error + close 触发两次
      ws.onerror = () => {};

      ws.onclose = () => {
        setConnected(false);
        if (closed || terminal) return;

        retries += 1;
        if (retries > MAX_RETRIES) {
          startPolling();
          return;
        }

        const delay = Math.min(1000 * 2 ** (retries - 1), MAX_BACKOFF_MS);
        reconnectTimer = window.setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      closed = true;
      if (reconnectTimer !== undefined) {
        window.clearTimeout(reconnectTimer);
      }
      stopPolling();
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [taskId]);

  return { stage, progress, message, connected };
}
