from fastapi import APIRouter, HTTPException, BackgroundTasks
from app.models.schemas import ProcessRequest
from app.pipeline.processor import processor
from app.api.websocket import create_ws_callback

router = APIRouter(prefix="/api/video", tags=["video"])


@router.post("/process")
async def process_video(request: ProcessRequest, background_tasks: BackgroundTasks):
    task_id = processor.create_task(request)
    processor.register_progress_callback(task_id, create_ws_callback(task_id))
    background_tasks.add_task(processor.process, task_id, request)
    return {"task_id": task_id}


@router.get("/{task_id}/status")
async def get_task_status(task_id: str):
    status = processor.get_task_status(task_id)
    if not status:
        raise HTTPException(status_code=404, detail="任务不存在")
    return status
