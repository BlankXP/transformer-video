from fastapi import WebSocket, WebSocketDisconnect
import json
import asyncio
from app.pipeline.processor import processor


class ConnectionManager:
    def __init__(self):
        self.active_connections: dict[str, list[WebSocket]] = {}

    async def connect(self, websocket: WebSocket, task_id: str):
        await websocket.accept()
        if task_id not in self.active_connections:
            self.active_connections[task_id] = []
        self.active_connections[task_id].append(websocket)

    def disconnect(self, websocket: WebSocket, task_id: str):
        if task_id in self.active_connections:
            self.active_connections[task_id].remove(websocket)
            if not self.active_connections[task_id]:
                del self.active_connections[task_id]

    async def send_progress(self, task_id: str, stage: str, progress: float, message: str):
        if task_id not in self.active_connections:
            return
        data = json.dumps({
            "task_id": task_id,
            "stage": stage,
            "progress": progress,
            "message": message,
        })
        disconnected = []
        for ws in self.active_connections[task_id]:
            try:
                await ws.send_text(data)
            except Exception:
                disconnected.append(ws)
        for ws in disconnected:
            self.disconnect(ws, task_id)


manager = ConnectionManager()


def create_ws_callback(task_id: str):
    def callback(tid: str, stage: str, progress: float, message: str):
        import asyncio
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.ensure_future(manager.send_progress(tid, stage, progress, message))
        except RuntimeError:
            pass
    return callback


async def websocket_endpoint(websocket: WebSocket, task_id: str):
    await manager.connect(websocket, task_id)
    status = processor.get_task_status(task_id)
    if status:
        await websocket.send_text(json.dumps({
            "task_id": status.task_id,
            "stage": status.stage,
            "progress": status.progress,
            "message": status.message,
        }))
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket, task_id)
