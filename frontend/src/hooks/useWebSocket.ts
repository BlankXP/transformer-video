import { useState, useEffect, useRef } from "react";
import type { TaskStage } from "../types";

interface WebSocketMessage {
  task_id: string;
  stage: TaskStage;
  progress: number;
  message: string;
}

export default function useWebSocket(taskId: string | null) {
  const [stage, setStage] = useState<TaskStage | null>(null);
  const [progress, setProgress] = useState<number>(0);
  const [message, setMessage] = useState<string>("");
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!taskId) return;

    // 同源 WebSocket:生产走 Nginx /ws 反代,开发走 Vite proxy;https 站点自动使用 wss
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws/${taskId}`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      const data: WebSocketMessage = JSON.parse(event.data);
      setStage(data.stage);
      setProgress(data.progress);
      setMessage(data.message);
    };

    ws.onerror = () => {};

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [taskId]);

  return { stage, progress, message };
}
