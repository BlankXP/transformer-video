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

    const wsUrl = `ws://${window.location.hostname}:8000/ws/${taskId}`;
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
